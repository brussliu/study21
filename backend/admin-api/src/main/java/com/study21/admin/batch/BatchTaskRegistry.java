package com.study21.admin.batch;

import com.study21.admin.classroomai.ClassroomAiNoteStep;
import com.study21.admin.geometryai.AiAssistGenerateStep;
import com.study21.admin.geometryai.AiFigureGenerateStep;
import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.setting.SettingRequirement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * バッチタスク定義レジストリ。
 *
 * <p>旧 study2.0 の全 41 バッチタスクを、study2.1 のタスク定義として登録する。
 * 各タスクは関連する設定ページ(page_code)と必須設定(setting_key)を宣言する。
 * ビジネスパラメータ・プロンプトはすべて設定テーブルから読み、既定値は持たない。</p>
 *
 * <p>2.1 で有効なのは <b>batS01（プロキシサービス）だけ</b>。2.0 の batL01（6 時間ごとの
 * プロキシ起動）を batS01 に改名し、実行のきっかけを「admin-api の起動時に 1 回」と
 * 「バッチ管理画面の【再実行】」に限った（定時・循環では動かさない）。他のバッチは
 * 既定値を無効にしたうえで、有効／無効は BAT_バッチコントロール情報 で管理する。</p>
 *
 * <p>設定値の型・有効値は COM_設定項目 カタログを単一の正とする（ここでは重複宣言しない）。</p>
 */
@Component
public class BatchTaskRegistry {

    private final List<BatchTaskDefinition> tasks;

    public BatchTaskRegistry() {
        List<BatchTaskDefinition> list = new ArrayList<>();
        // ---- 英語単語 ----
        list.add(task("batC01", BatchTaskType.C, "英語→中国語翻訳（STY_単語情報）", false,
                "TRANSLATION", "ZH_WORD_TRANSLATE_API", "ZH_SENTENCE_TRANSLATE_API", "ZH_TRANSLATE_THREADS"));
        list.add(task("batC02", BatchTaskType.C, "英語→日本語翻訳（STY_単語情報）", false,
                "TRANSLATION", "JA_WORD_TRANSLATE_API", "JA_SENTENCE_TRANSLATE_API", "JA_TRANSLATE_THREADS"));
        list.add(task("batC03", BatchTaskType.C, "英語発音取得（単語/例句 mp3）", false,
                "VOICE", "EN_WORD_VOICE_API", "EN_SENTENCE_VOICE_API", "EN_VOICE_THREADS"));

        // ---- 英単語問題生成 ----
        list.add(task("batC04", BatchTaskType.C, "初級編 英訳中日問題生成（STY_単語質問情報）", false,
                "WORD_QUESTION", "BAT_C04_AI_MODEL", "BAT_C04_THREADS",
                "BAT_C04_SYSTEM_PROMPT_ZH", "BAT_C04_USER_PROMPT_ZH",
                "BAT_C04_SYSTEM_PROMPT_JA", "BAT_C04_USER_PROMPT_JA"));
        list.add(task("batC22", BatchTaskType.C, "中級編D.英訳中日問題データ生成", false,
                "WORD_QUESTION", "BAT_C04_2_AI_PROVIDER", "BAT_C04_2_BATCH_SIZE", "BAT_C04_2_THREADS",
                "BAT_C04_2_REQUEST_TIMEOUT_SECONDS", "BAT_C04_2_MAX_COMPLETION_TOKENS", "BAT_C04_2_TEMPERATURE",
                "BAT_C04_2_SYSTEM_PROMPT", "BAT_C04_2_USER_PROMPT", "BAT_C04_2_RETRY_LIMIT"));
        list.add(task("batC23", BatchTaskType.C, "中級編E.文脈英訳問題データ生成", false,
                "WORD_QUESTION", "BAT_C04_3_AI_PROVIDER", "BAT_C04_3_BATCH_SIZE", "BAT_C04_3_THREADS",
                "BAT_C04_3_REQUEST_TIMEOUT_SECONDS", "BAT_C04_3_MAX_COMPLETION_TOKENS", "BAT_C04_3_TEMPERATURE",
                "BAT_C04_3_SYSTEM_PROMPT", "BAT_C04_3_USER_PROMPT", "BAT_C04_3_RETRY_LIMIT"));
        list.add(task("batC10", BatchTaskType.C, "AI誤問題下書き補完", false, "AI_MODEL",
                "AI_QWEN_MODEL", "AI_QWEN_API_KEY", "AI_QWEN_URL"));

        // ---- 英熟語 ----
        list.add(task("batC31", BatchTaskType.C, "英熟語標準化及び分類", false,
                "ENGLISH_PHRASE_DETAIL_AI",
                "ENGLISH_PHRASE_STANDARDIZATION_AI_PROVIDER", "ENGLISH_PHRASE_STANDARDIZATION_AI_BATCH_SIZE",
                "ENGLISH_PHRASE_STANDARDIZATION_AI_THREADS", "ENGLISH_PHRASE_STANDARDIZATION_AI_REQUEST_TIMEOUT_SECONDS",
                "ENGLISH_PHRASE_STANDARDIZATION_AI_MAX_COMPLETION_TOKENS", "ENGLISH_PHRASE_STANDARDIZATION_AI_TEMPERATURE",
                "ENGLISH_PHRASE_STANDARDIZATION_AI_AUTO_APPROVE_THRESHOLD",
                "ENGLISH_PHRASE_STANDARDIZATION_AI_SYSTEM_PROMPT", "ENGLISH_PHRASE_STANDARDIZATION_AI_USER_PROMPT",
                "ENGLISH_PHRASE_STANDARDIZATION_AI_RETRY_LIMIT"));
        list.add(task("batC32", BatchTaskType.C, "英熟語詳細情報AI取得", false,
                "ENGLISH_PHRASE_DETAIL_AI",
                "ENGLISH_PHRASE_DETAIL_AI_PROVIDER", "ENGLISH_PHRASE_DETAIL_AI_BATCH_SIZE",
                "ENGLISH_PHRASE_DETAIL_AI_THREADS", "ENGLISH_PHRASE_DETAIL_AI_REQUEST_TIMEOUT_SECONDS",
                "ENGLISH_PHRASE_DETAIL_AI_MAX_COMPLETION_TOKENS", "ENGLISH_PHRASE_DETAIL_AI_TEMPERATURE",
                "ENGLISH_PHRASE_DETAIL_AI_SYSTEM_PROMPT", "ENGLISH_PHRASE_DETAIL_AI_USER_PROMPT",
                "ENGLISH_PHRASE_DETAIL_AI_RETRY_LIMIT"));
        list.add(task("batC33", BatchTaskType.C, "熟語D.表現意味選択問題データ生成", false,
                "WORD_QUESTION", "BAT_C33_AI_PROVIDER", "BAT_C33_BATCH_SIZE", "BAT_C33_THREADS",
                "BAT_C33_REQUEST_TIMEOUT_SECONDS", "BAT_C33_MAX_COMPLETION_TOKENS", "BAT_C33_TEMPERATURE",
                "BAT_C33_SYSTEM_PROMPT", "BAT_C33_USER_PROMPT", "BAT_C33_RETRY_LIMIT"));
        list.add(task("batC34", BatchTaskType.C, "熟語E.文脈意味選択問題データ生成", false,
                "WORD_QUESTION", "BAT_C34_AI_PROVIDER", "BAT_C34_BATCH_SIZE", "BAT_C34_THREADS",
                "BAT_C34_REQUEST_TIMEOUT_SECONDS", "BAT_C34_MAX_COMPLETION_TOKENS", "BAT_C34_TEMPERATURE",
                "BAT_C34_SYSTEM_PROMPT", "BAT_C34_USER_PROMPT", "BAT_C34_RETRY_LIMIT"));

        // ---- 日本語単語 ----
        list.add(task("batC41", BatchTaskType.C, "日本語単語 詳細情報AI取得（A・B共通）", false,
                "JAPANESE_WORD_AI", "BAT_C41_AI_PROVIDER", "BAT_C41_BATCH_MAX", "BAT_C41_THREADS",
                "BAT_C41_REQUEST_TIMEOUT_SECONDS", "BAT_C41_MAX_COMPLETION_TOKENS", "BAT_C41_TEMPERATURE",
                "BAT_C41_SYSTEM_PROMPT", "BAT_C41_USER_PROMPT", "BAT_C41_RETRY_LIMIT"));
        list.add(task("batC42", BatchTaskType.C, "日本語単語 C.読み問題AI取得", false,
                "JAPANESE_WORD_AI", "BAT_C42_AI_PROVIDER", "BAT_C42_BATCH_MAX", "BAT_C42_THREADS",
                "BAT_C42_REQUEST_TIMEOUT_SECONDS", "BAT_C42_MAX_COMPLETION_TOKENS", "BAT_C42_TEMPERATURE",
                "BAT_C42_SYSTEM_PROMPT", "BAT_C42_USER_PROMPT", "BAT_C42_RETRY_LIMIT"));
        list.add(task("batC43", BatchTaskType.C, "日本語単語 D.文脈問題AI取得", false,
                "JAPANESE_WORD_AI", "BAT_C43_AI_PROVIDER", "BAT_C43_BATCH_MAX", "BAT_C43_THREADS",
                "BAT_C43_REQUEST_TIMEOUT_SECONDS", "BAT_C43_MAX_COMPLETION_TOKENS", "BAT_C43_TEMPERATURE",
                "BAT_C43_SYSTEM_PROMPT", "BAT_C43_USER_PROMPT", "BAT_C43_RETRY_LIMIT"));
        list.add(task("batC44", BatchTaskType.C, "日本語単語 E.漢字問題AI取得", false,
                "JAPANESE_WORD_AI", "BAT_C44_AI_PROVIDER", "BAT_C44_BATCH_MAX", "BAT_C44_THREADS",
                "BAT_C44_REQUEST_TIMEOUT_SECONDS", "BAT_C44_MAX_COMPLETION_TOKENS", "BAT_C44_TEMPERATURE",
                "BAT_C44_SYSTEM_PROMPT", "BAT_C44_USER_PROMPT", "BAT_C44_RETRY_LIMIT"));

        // ---- 英作文 ----
        list.add(task("batC11", BatchTaskType.C, "英作文 画像分類・OCR・主題タイトル生成", false,
                "ENGLISH_ESSAY", "ENGLISH_ESSAY_ENABLED", "ENGLISH_ESSAY_OCR_AI_PROVIDER",
                "ENGLISH_ESSAY_TITLE_AI_PROVIDER", "ENGLISH_ESSAY_MAX_IMAGES", "ENGLISH_ESSAY_MAX_IMAGE_MB",
                "ENGLISH_ESSAY_OCR_MAX_IMAGE_PIXELS", "ENGLISH_ESSAY_OCR_REQUEST_TIMEOUT_SECONDS",
                "ENGLISH_ESSAY_OCR_RETRY_LIMIT", "ENGLISH_ESSAY_OCR_PROMPT", "ENGLISH_ESSAY_OCR_USER_PROMPT",
                "ENGLISH_ESSAY_TITLE_PROMPT", "ENGLISH_ESSAY_TITLE_USER_PROMPT"));
        list.add(task("batC12", BatchTaskType.C, "英作文 英検基準AI添削", false,
                "ENGLISH_ESSAY", "ENGLISH_ESSAY_ENABLED", "ENGLISH_ESSAY_GRADING_AI_PROVIDER",
                "ENGLISH_ESSAY_GRADING_REQUEST_TIMEOUT_SECONDS", "ENGLISH_ESSAY_GRADING_RETRY_LIMIT",
                "ENGLISH_ESSAY_GRADING_PROMPT", "ENGLISH_ESSAY_GRADING_USER_PROMPT"));

        // ---- 英語穴埋め ----
        list.add(task("batC13", BatchTaskType.C, "英語穴埋め問題 OCR・構造化", false,
                "ENGLISH_CLOZE", "ENGLISH_CLOZE_MAX_IMAGES", "ENGLISH_CLOZE_MAX_IMAGE_MB",
                "ENGLISH_CLOZE_OCR_AI_PROVIDER", "ENGLISH_CLOZE_OCR_MAX_IMAGE_PIXELS",
                "ENGLISH_CLOZE_OCR_TIMEOUT_SECONDS", "ENGLISH_CLOZE_OCR_RETRY_LIMIT",
                "ENGLISH_CLOZE_OCR_SYSTEM_PROMPT", "ENGLISH_CLOZE_OCR_USER_PROMPT"));
        list.add(task("batC14", BatchTaskType.C, "英語穴埋め問題別AI解説生成", false,
                "ENGLISH_CLOZE", "ENGLISH_CLOZE_EXPLANATION_AI_PROVIDER", "ENGLISH_CLOZE_EXPLANATION_BATCH_MAX",
                "ENGLISH_CLOZE_EXPLANATION_THREADS", "ENGLISH_CLOZE_EXPLANATION_REQUEST_TIMEOUT_SECONDS",
                "ENGLISH_CLOZE_EXPLANATION_RETRY_LIMIT", "ENGLISH_CLOZE_EXPLANATION_SYSTEM_PROMPT",
                "ENGLISH_CLOZE_EXPLANATION_USER_PROMPT"));

        // ---- 英語長文精読 ----
        list.add(task("batC15", BatchTaskType.C, "英語長文精読 OCR結果集計・最終整形", false,
                "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_BAT_C15_AI_PROVIDER"));
        list.add(task("batC15-1", BatchTaskType.C, "英語長文精読 方式A 設問画像OCR・構造化", false,
                "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_METHOD_A_ARTICLE_OCR_PROVIDER",
                "ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_SYSTEM_PROMPT",
                "ENGLISH_READING_INTENSIVE_OCR_METHOD_A_STRUCTURE_USER_PROMPT",
                "ENGLISH_READING_INTENSIVE_OCR_ARTICLE_SYSTEM_PROMPT", "ENGLISH_READING_INTENSIVE_OCR_ARTICLE_USER_PROMPT"));
        list.add(task("batC15-2", BatchTaskType.C, "英語長文精読 方式A 設問画像OCR・構造化（一時無効）", false,
                "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_OCR_QUESTION_SYSTEM_PROMPT",
                "ENGLISH_READING_INTENSIVE_OCR_QUESTION_USER_PROMPT"));
        list.add(task("batC15-3", BatchTaskType.C, "英語長文精読 方式B OCR文字抽出・AI構造化", false,
                "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_METHOD_B_OCR_PROVIDER",
                "ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_SYSTEM_PROMPT", "ENGLISH_READING_INTENSIVE_OCR_STRUCTURE_USER_PROMPT"));
        list.add(task("batC16", BatchTaskType.C, "英語長文精読 基本情報・導読抽出", false,
                "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_GUIDE_AI_PROVIDER",
                "ENGLISH_READING_INTENSIVE_GUIDE_TIMEOUT_SECONDS",
                "ENGLISH_READING_INTENSIVE_GUIDE_SYSTEM_PROMPT", "ENGLISH_READING_INTENSIVE_GUIDE_USER_PROMPT"));
        list.add(task("batC17", BatchTaskType.C, "英語長文精読 解説・重点語彙生成", false,
                "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_EXPLANATION_AI_PROVIDER",
                "ENGLISH_READING_INTENSIVE_EXPLANATION_TIMEOUT_SECONDS", "ENGLISH_READING_INTENSIVE_EXPLANATION_BATCH_SIZE",
                "ENGLISH_READING_INTENSIVE_EXPLANATION_THREADS",
                "ENGLISH_READING_INTENSIVE_EXPLANATION_SYSTEM_PROMPT", "ENGLISH_READING_INTENSIVE_EXPLANATION_USER_PROMPT"));
        list.add(task("batC18", BatchTaskType.C, "英語長文精読 設問解析", false,
                "ENGLISH_READING_INTENSIVE", "ENGLISH_READING_INTENSIVE_QUESTION_AI_PROVIDER",
                "ENGLISH_READING_INTENSIVE_QUESTION_TIMEOUT_SECONDS",
                "ENGLISH_READING_INTENSIVE_QUESTION_SYSTEM_PROMPT", "ENGLISH_READING_INTENSIVE_QUESTION_USER_PROMPT"));

        // ---- AI共通・OCR ----
        list.add(task("batC07", BatchTaskType.C, "AI内容生成（STY_AI内容情報）", false,
                "AI_MODEL", "AI_QWEN_MODEL", "AI_QWEN_API_KEY", "AI_QWEN_URL",
                "AI_CHATGPT_MODEL", "AI_CHATGPT_API_KEY", "AI_CHATGPT_URL"));
        list.add(task("batC09", BatchTaskType.C, "AI OCR（画像/PDF文字認識）", false,
                "AI_MODEL", "AI_BIGMODEL_OCR_API_KEY", "AI_BIGMODEL_OCR_URL", "AI_BIGMODEL_OCR_MODEL"));
        list.add(task("batC91", BatchTaskType.C, "AI OCR（智譜OCR 共通処理）", false,
                "AI_MODEL", "AI_BIGMODEL_OCR_API_KEY", "AI_BIGMODEL_OCR_URL", "AI_BIGMODEL_OCR_MODEL"));

        // ---- 英単語教材・詳細 ----
        list.add(task("batC19", BatchTaskType.C, "英単語教材取込 AI認識（Qwen-VL-OCR Batch）", false,
                "ENGLISH_WORD_TEXTBOOK_AI", "ENGLISH_WORD_TEXTBOOK_AI_PROVIDER",
                "ENGLISH_WORD_TEXTBOOK_AI_COMPLETION_WINDOW", "ENGLISH_WORD_TEXTBOOK_AI_SYSTEM_PROMPT",
                "ENGLISH_WORD_TEXTBOOK_AI_USER_PROMPT", "ENGLISH_WORD_TEXTBOOK_AI_MAX_IMAGE_PIXELS",
                "ENGLISH_WORD_TEXTBOOK_AI_TIMEOUT_SECONDS", "ENGLISH_WORD_TEXTBOOK_AI_MAX_CONCURRENCY"));
        list.add(task("batC21", BatchTaskType.C, "英単語詳細情報AI取得", false,
                "ENGLISH_WORD_DETAIL_AI", "ENGLISH_WORD_DETAIL_AI_PROVIDER", "ENGLISH_WORD_DETAIL_AI_BATCH_SIZE",
                "ENGLISH_WORD_DETAIL_AI_THREADS", "ENGLISH_WORD_DETAIL_AI_REQUEST_TIMEOUT_SECONDS",
                "ENGLISH_WORD_DETAIL_AI_MAX_COMPLETION_TOKENS", "ENGLISH_WORD_DETAIL_AI_TEMPERATURE",
                "ENGLISH_WORD_DETAIL_AI_SYSTEM_PROMPT", "ENGLISH_WORD_DETAIL_AI_USER_PROMPT",
                "ENGLISH_WORD_DETAIL_AI_RETRY_LIMIT"));

        // ---- 学習モニター ----
        list.add(loop("batL02", "学習モニター動画取込・スナップショット切出（5分ごと、最大5ファイル）", 5, null,
                "STUDY_MONITOR", "STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY", "STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY",
                "STUDY_MONITOR_VIDEO_PROCESSING_START_TIME", "STUDY_MONITOR_VIDEO_PROCESSING_END_TIME",
                "STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS", "STUDY_MONITOR_CAMERA_LOCATION"));
        list.add(loop("batL03", "学習モニター スナップショットAI分析（5分ごと）", 5, null,
                "STUDY_MONITOR", "STUDY_MONITOR_AI_BATCH_LIMIT", "STUDY_MONITOR_AI_THREADS",
                "STUDY_MONITOR_AI_TIMEOUT_SECONDS", "STUDY_MONITOR_AI_IMAGE_RESOLUTION",
                "STUDY_MONITOR_FIRST_AI_PROVIDER", "STUDY_MONITOR_FIRST_SYSTEM_PROMPT",
                "STUDY_MONITOR_FIRST_USER_PROMPT"));

        // ---- 図形管理（AI生図 / AI画図助手）----
        // 利用者の指示で、AI 生図の流水線は「前処理（通常コード）→ batC51（AI 生成）→ 検証（通常コード）」
        // に変更した。バッチとして登録するのは AI 生成の batC51 だけ（前処理・検証はバッチではない）。
        // AI 画図助手もバッチ（batC52）として実行する（依頼 → その場で即時実行）。
        // 種別 C（呼出）＝画面から随時実行する。S（起動時）にはしない（起動時に AI を呼ばない）。
        // 必須設定は**工程クラスが宣言したもの**を使う（定義と実装が食い違わないように）
        // AI 生図の AI 生成は**モードごとに 1 バッチ**（batC51-A〜D）。連字符つきの接尾辞は
        // 既存の batC15-1〜3 と同じ扱い（バッチコードの列は VARCHAR(20) で収まる）。
        // 必須設定はモード共通の分（モード別のプロンプト・モデルパラメータは「未設定なら共通を継承」）
        for (FigureMode mode : FigureMode.values()) {
            list.add(new BatchTaskDefinition(mode.taskCode(), BatchTaskType.C,
                    "AI生図 " + mode.label() + "（GeoGebra コマンド生成）", false, null, null,
                    "GEOMETRY_AI", AiFigureGenerateStep.REQUIRED_SETTINGS));
        }
        // 歴史的な batC51（モードが無い時代の要求・実行履歴）用の入口。要求行が無いときは
        // モード A の生成待ちを拾う（利用者の指示: 歴史的な要求は A として扱う）
        list.add(new BatchTaskDefinition(FigureMode.LEGACY_TASK_CODE, BatchTaskType.C,
                "AI生図 AI生成（歴史的なコード。モード A として処理）", false, null, null,
                "GEOMETRY_AI", AiFigureGenerateStep.REQUIRED_SETTINGS));
        list.add(new BatchTaskDefinition("batC52", BatchTaskType.C,
                "AI画図助手 生成", false, null, null,
                "GEOMETRY_AI", AiAssistGenerateStep.REQUIRED_SETTINGS));

        // ---- 授業録音 / AI 授業記録 ----
        // フェーズ分析（batC61）と最終まとめ（batC62）。種別 C（画面から随時実行）。
        // 起動は admin-api の薄い入口（ClassroomAiPipelineService）がノートの種別で 61 か 62 を呼ぶ。
        // 必須設定は工程クラス（ClassroomAiNoteStep）が宣言したものを使う（定義と実装が食い違わないように）
        list.add(new BatchTaskDefinition("batC61", BatchTaskType.C,
                "授業ノート フェーズ分析", false, null, null,
                "CLASSROOM_AI", ClassroomAiNoteStep.PHASE_REQUIRED_SETTINGS));
        list.add(new BatchTaskDefinition("batC62", BatchTaskType.C,
                "授業ノート 最終まとめ生成", false, null, null,
                "CLASSROOM_AI", ClassroomAiNoteStep.SUMMARY_REQUIRED_SETTINGS));

        // ---- システム ----
        // batS01: 2.0 の batL01（プロキシサービス）を改名したもの。種別 S =
        // admin-api の起動時に 1 回だけ実行し、あとは画面の【再実行】で動かす。
        // 2.0 は 6 時間ごと（0/6/12/18 時）にも実行していたが、2.1 では行わない。
        list.add(new BatchTaskDefinition("batS01", BatchTaskType.S, "プロキシサービス（admin-api 起動時と再実行）",
                true, null, null, "SYSTEM", List.of()));
        list.add(new BatchTaskDefinition("batR01", BatchTaskType.R, "「STY_日次情報」テーブル生成処理",
                false, null, null, "SYSTEM", List.of()));
        // batR02 は 2.1 で「AI 生図の画像（保持日数を過ぎたもの）の削除」を実装した。
        // 実行履歴・上網履歴の削除は今後の実装（定義と名前はその用途のまま）。
        list.add(new BatchTaskDefinition("batR02", BatchTaskType.R,
                "バッチ実行履歴・上網履歴クリーンアップ処理（AI生図の画像も削除）",
                false, null, null, "SYSTEM", List.of()));
        list.add(new BatchTaskDefinition("batR03", BatchTaskType.R, "インターネット利用終了（23:30）",
                false, null, null, "SYSTEM", List.of()));
        list.add(new BatchTaskDefinition("batR04", BatchTaskType.R, "インターネット利用開始（06:30）",
                false, null, null, "SYSTEM", List.of()));
        list.add(task("batR05", BatchTaskType.R, "学習タスク未実施リマインド（21:00）", false,
                "DAILY_REPORT", "DAILY_REPORT_REMINDER_ENABLED"));

        this.tasks = List.copyOf(list);
    }

    public List<BatchTaskDefinition> findAll() {
        return tasks;
    }

    public BatchTaskDefinition findByCode(String taskCode) {
        return tasks.stream()
                .filter(t -> t.taskCode().equals(taskCode))
                .findFirst()
                .orElse(null);
    }

    private static BatchTaskDefinition task(String code, BatchTaskType type, String desc, boolean active,
                                            String pageCode, String... keys) {
        return new BatchTaskDefinition(code, type, desc, active, null, null, pageCode, reqs(pageCode, keys));
    }

    private static BatchTaskDefinition loop(String code, String desc, int loopEveryMinutes, Integer minuteOfHour,
                                            String pageCode, String... keys) {
        return new BatchTaskDefinition(code, BatchTaskType.L, desc, false, loopEveryMinutes, minuteOfHour,
                pageCode, reqs(pageCode, keys));
    }

    private static List<SettingRequirement> reqs(String pageCode, String... keys) {
        List<SettingRequirement> list = new ArrayList<>();
        for (String key : keys) {
            list.add(new SettingRequirement(pageCode, key));
        }
        return List.copyOf(list);
    }
}
