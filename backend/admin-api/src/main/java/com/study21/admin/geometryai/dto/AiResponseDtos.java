package com.study21.admin.geometryai.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * バッチコード → AI 出力 DTO の対応表（{@link AiResponseSchemaService} とセットで使う唯一の登録所）。
 *
 * <p>新しいバッチで AI の出力を DTO 化するときは、DTO を作ってここに 1 行足すだけでよい
 * （プロンプトへの注入も Data TAB の表示も、同じ仕組みがそのまま効く）。</p>
 */
public final class AiResponseDtos {

    private static final Map<String, Class<?>> BY_TASK_CODE = new LinkedHashMap<>();

    static {
        // AI 生図はモードごとに**独立したバッチ**（batC51-A〜D）で、出力 DTO もモードごとに別。
        // 連字符つきの接尾辞は既存の batC15-1〜3 と同じ扱い（BAT_バッチコントロール情報・
        // BAT_バッチ実行履歴情報 のバッチコードはどちらも VARCHAR(20) で収まる）。
        for (FigureMode mode : FigureMode.values()) {
            BY_TASK_CODE.put(mode.taskCode(), mode.dtoClass());
        }
        // 歴史的な batC51（モードが無い時代の要求・実行履歴）は今までどおりの DTO で読む
        BY_TASK_CODE.put(FigureMode.LEGACY_TASK_CODE, BatC51ResultDto.class);
        BY_TASK_CODE.put("batC52", BatC52ResultDto.class);
    }

    private AiResponseDtos() {
    }

    /** バッチコードに対応する DTO（未登録なら空）。 */
    public static Optional<Class<?>> dtoOf(String taskCode) {
        if (taskCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_TASK_CODE.get(taskCode.trim()));
    }

    /** 登録済みのバッチコード（登録順）。 */
    public static Map<String, Class<?>> all() {
        return Map.copyOf(BY_TASK_CODE);
    }
}
