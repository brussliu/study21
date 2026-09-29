package com.study21.admin.controller;

import com.study21.admin.batch.BatchService;
import com.study21.admin.japanesewordai.JapaneseWordAiLimits;
import com.study21.admin.japanesewordai.JapaneseWordAiQueue;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.exception.ValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日本語単語の AI 取得（{@code batC41}〜{@code batC44}）の**受付**（admin-api・薄い入口）。
 *
 * <p><b>なぜ専用の入口が要るか</b>: この 4 つは<b>種別 C（呼出）</b>なので、
 * バッチ管理画面の【再実行】は出ない（`BatchTaskDefinition#canManualRerun()` が C を false にする。
 * `docs/DECISIONS.md` の「なぜ種別 C のバッチに【再実行】ボタンを出さないか」）。C の本来の入口は
 * <b>他の処理からの {@code BatchService#rerunStep}</b> なので、日本語単語の画面から起動するための
 * 入口をここに置く（AI 生図の {@code /api/admin/batch/geometry-ai/**} と同じ考え方）。</p>
 *
 * <p><b>非同期（2026-09-27 改修）</b>: ここは<b>受付だけ</b>する。語 × 内容種別を
 * {@code JPN_AI生成履歴情報} に {@code QUEUED} で積み、すぐ返す（AI は呼ばない）。実行は
 * バックエンドの働き手（{@code JapaneseWordAiWorker}）が行うので、<b>画面を閉じても処理は続く</b>。
 * 進み具合は一覧の「取得状態」（取得中＝黄）で見え、終わった内容は【詳細】で確かめる
 * （以前は同期で、AI の完了まで HTTP が返らなかった）。</p>
 *
 * <p><b>認証</b>: いまは `/api/admin/batch/**` と同じく許可されている（`SecurityConfig` の注記どおり、
 * 認証の実装は今回の範囲外）。利用者の権限で守るなら、授業まとめと同じく
 * user-api の入口＋{@code X-Internal-Token} に寄せること。</p>
 */
@RestController
@RequestMapping("/api/admin/batch/japanese-word-ai")
public class JapaneseWordAiBatchController {

    /**
     * 1 回の受付で受け付ける最大の語数は**設定ページの「1 回の最大単語数」**（≤200）で決まる。
     * 読めないときだけ {@link JapaneseWordAiLimits#MAX_WORDS_PER_ACCEPT} へ落とす。
     */
    private int acceptLimitOf(String batchCode) {
        return JapaneseWordAiLimits.limitOf(settingsService, batchCode);
    }

    /** 取得区分 → バッチコード。 */
    private static final Map<String, String> BATCH_OF_KIND = JapaneseWordAiLimits.BATCH_OF_KIND;

    private final JapaneseWordAiQueue queue;
    private final SettingsService settingsService;
    private final BatchService batchService;

    public JapaneseWordAiBatchController(JapaneseWordAiQueue queue, SettingsService settingsService,
                                         BatchService batchService) {
        this.queue = queue;
        this.settingsService = settingsService;
        this.batchService = batchService;
    }

    /**
     * 取得区分ごとの**1 回の受付の上限**（設定ページの「1 回の最大単語数」と受付の上限の小さい方）。
     *
     * <p>画面（AI 取得の窓）が「1 回の受付は N 語までです」と出すために読む。固定値を画面に書くと、
     * 設定を変えたときに表示と実際の処理数がずれる（設定値を読んで渡す。2026-09-26）。</p>
     */
    @GetMapping("/limits")
    public ApiResponse<Map<String, Map<String, Object>>> limits() {
        return ApiResponse.ok(JapaneseWordAiLimits.byKind(settingsService),
                "AI 取得の上限を返しました。");
    }

    /**
     * AI 取得を**受け付ける**（実行はバックエンドの働き手）。
     *
     * <p>本文は {@code {"kind":"DETAIL|C|D|E","wordIds":[...]}}。{@code operator} は画面が送るが
     * 受付では実行履歴を作らないので**使わない**（実行は働き手が行い、追跡は
     * {@code JPN_AI生成履歴情報} と {@code BAT_AI呼出履歴情報} で行う）。</p>
     */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> run(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String kind = text(body.get("kind"));
        String batchCode = kind == null ? null : BATCH_OF_KIND.get(kind);
        if (batchCode == null) {
            throw new ValidationException("取得区分は DETAIL / C / D / E のいずれかです。");
        }
        List<Long> wordIds = wordIdsOf(body.get("wordIds"));
        if (wordIds.isEmpty()) {
            throw new ValidationException("取得する語を選んでください（表示中の語がありません）。");
        }
        // 上限は**設定ページの「1 回の最大単語数」**（≤200）。画面は同じ値で対象を絞るが、
        // 画面を通さない呼出もあるのでここでも検証する
        int acceptLimit = acceptLimitOf(batchCode);
        if (wordIds.size() > acceptLimit) {
            throw new ValidationException("一度に受付できるのは " + acceptLimit + " 語までです"
                    + "（設定ページの「1 回の最大単語数」）。語数を分けて受付けてください。");
        }
        // バッチ一覧で無効にされているときは受け付けない（バッチ一覧のスイッチをそのまま効かせる。
        // 同期実行の入口は rerunStep の中で同じ検査をしていた）
        batchService.requireCallable(batchCode);
        Map<String, Object> response = new LinkedHashMap<>(queue.accept(kind, wordIds));
        response.put("batchCode", batchCode);
        response.put("kind", kind);
        response.put("wordIds", wordIds);
        return ApiResponse.ok(response, "日本語単語の AI 取得（" + kind + "）を受付けました"
                + "（受付 " + response.get("accepted") + " 件 / 実行中 " + response.get("reused") + " 件）。"
                + "バックグラウンドで取得します。");
    }

    /** 受付けた取得の進み具合を返す（画面の様子見が使う）。 */
    @GetMapping("/progress")
    public ApiResponse<Map<String, Object>> progress(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String generationIds) {
        return ApiResponse.ok(queue.progress(generationIdListOf(generationIds)),
                "AI 取得の進み具合を返しました。");
    }

    /** 語の一覧（数値として読めるものだけ）。 */
    static List<Long> wordIdsOf(Object value) {
        List<Long> ids = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return ids;
        }
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
            } else if (item instanceof String text && text.matches("\\d+")) {
                ids.add(Long.parseLong(text));
            }
        }
        return ids;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** カンマ区切りの生成 ID（空・数値でないものは無視する）。 */
    static List<Long> generationIdListOf(String value) {
        List<Long> ids = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return ids;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.matches("\\d+")) {
                ids.add(Long.parseLong(trimmed));
            }
        }
        return ids;
    }
}
