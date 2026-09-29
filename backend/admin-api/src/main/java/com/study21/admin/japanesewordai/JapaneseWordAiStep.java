package com.study21.admin.japanesewordai;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.setting.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * batC41〜batC44 の業務処理（2.0 の {@code JapaneseAiService} の移行）。
 *
 * <p>流れは 2.0 と同じ:</p>
 * <ol>
 *   <li>設定を解決する（{@link JapaneseWordAiSettings}。**2.1 は既定値を持たない**）</li>
 *   <li>対象の語を選ぶ（選定は SQL 側。既に成功している語は除かれる）</li>
 *   <li>{@code THREADS} の本数で並列に、1 語ずつ AI へ渡す（{@code RETRY_LIMIT} だけ再試行）</li>
 *   <li>{@code JPN_AI生成履歴情報} に 1 語 × 1 内容種別の行を残し、取れた内容を書き込む</li>
 * </ol>
 *
 * <p><strong>書き込みの規則</strong>（2.0 から引き継いだ安全側の判断）:</p>
 * <ul>
 *   <li>詳細は<b>新しい版を作る</b>（上書きしない。内容版数は SQL 側で上げる）</li>
 *   <li>問題は「同じ語・同じ種別の ACTIVE を ARCHIVED にしてから入れる」。
 *       <b>消さない</b>のは、{@code JPN_テスト出題情報.問題ID} が参照しているため。
 *       1 回の書き込みで入れるのは<b>その回の内容種別の題だけ</b>（C の応答は C1・C2 の
 *       2 件が返るが、内容種別ごとに呼ぶので二重に書かない）</li>
 *   <li>失敗した語は<b>内容を書かない</b>（中途半端な詳細・問題を残さない）</li>
 *   <li>一部が失敗しても成功した語は残す。**1 語も成功しなければ例外**にして実行履歴を FAILED にする</li>
 * </ul>
 */
@Component
public class JapaneseWordAiStep {

    private static final Logger log = LoggerFactory.getLogger(JapaneseWordAiStep.class);

    /** 再試行の待ち（2.0 と同じ 2s / 4s / 8s、上限 8 秒）。 */
    private static final long[] BACKOFF_MS = {2_000L, 4_000L, 8_000L};

    /** 内容種別コード → その内容を書く先（詳細か、問題の種別か）。 */
    private static final String DETAIL_TYPE = "A_DETAIL";

    private final JapaneseWordAiMapper mapper;
    private final JapaneseWordAiClient aiClient;
    private final JapaneseWordAiDetailReader detailReader;
    private final JapaneseWordAiDetailWriter detailWriter;
    private final SettingsService settingsService;
    private final GeometryAiConnectionResolver connectionResolver;
    /**
     * 設定の System Prompt の後ろに「出力形式（JSON Schema）」を足す部品。
     *
     * <p>出力データ構造の定義は DTO（{@code JapaneseWordAiDtos}）が唯一なので、プロンプトに
     * JSON を手書きしない。DTO を直せば、次の実行からそのままプロンプトへ反映される
     * （設定ページの Data TAB も同じ生成結果を見る）。</p>
     */
    private final AiResponseFormatPrompt responseFormatPrompt;

    public JapaneseWordAiStep(JapaneseWordAiMapper mapper,
                              JapaneseWordAiClient aiClient,
                              JapaneseWordAiDetailReader detailReader,
                              JapaneseWordAiDetailWriter detailWriter,
                              SettingsService settingsService,
                              GeometryAiConnectionResolver connectionResolver,
                              AiResponseFormatPrompt responseFormatPrompt) {
        this.mapper = mapper;
        this.aiClient = aiClient;
        this.detailReader = detailReader;
        this.detailWriter = detailWriter;
        this.settingsService = settingsService;
        this.connectionResolver = connectionResolver;
        this.responseFormatPrompt = responseFormatPrompt;
    }

    /**
     * 1 回の実行。
     *
     * @param execution 実行履歴（実行ID を呼出履歴へ写す）
     * @param batchCode バッチコード（{@code batC41} など）
     * @param kind      取得区分（{@code DETAIL} / {@code C} / {@code D} / {@code E}）
     * @param wordIds   画面から選んだ語（空なら条件に合う語を選ぶ）
     */
    public Map<String, Object> run(BatchExecutionEntity execution, String batchCode, String kind,
                                   List<Long> wordIds) {
        long startedMs = System.currentTimeMillis();
        JapaneseWordAiSettings settings = JapaneseWordAiSettings.of(
                settingsService.requireSettings(batchCode, JapaneseWordAiSettings.requirements(batchCode)),
                batchCode);
        List<String> contentTypes = JapaneseWordAiSettings.contentTypesOf(kind);

        // 対象の選定は SQL。内容種別ごとに「まだ成功していない語」を引く（重複は畳む）
        Map<Long, JapaneseWordAiMapper.AiCallTarget> targets = new LinkedHashMap<>();
        for (String contentType : contentTypes) {
            for (JapaneseWordAiMapper.AiCallTarget target
                    : mapper.findTargets(contentType, wordIds == null ? List.of() : wordIds, settings.batchMax())) {
                targets.putIfAbsent(target.getWordId(), target);
            }
        }
        if (targets.isEmpty()) {
            return result("対象=0件（" + kind + " の取得がまだの単語はありません）", 0, 0, 0, 0, startedMs);
        }

        GeometryAiConnectionResolver.AiConnection connection =
                connectionResolver.resolve(batchCode, settings.provider());

        List<JapaneseWordAiMapper.AiCallTarget> list = new ArrayList<>(targets.values());
        int threads = settings.threadsFor(list.size());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<WordResult>> futures = new ArrayList<>();
        try {
            for (JapaneseWordAiMapper.AiCallTarget target : list) {
                futures.add(executor.submit(() -> processWord(execution, batchCode, kind, contentTypes,
                        target, settings, connection)));
            }
            int succeeded = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();
            for (Future<WordResult> future : futures) {
                WordResult wordResult = await(future);
                if (wordResult.succeeded()) {
                    succeeded += 1;
                } else {
                    failed += 1;
                    if (errors.size() < 3) {
                        // 人が実行履歴だけで原因を追えるように、コードと理由の両方を載せる
                        errors.add(wordResult.heading() + ": "
                                + (wordResult.errorCode() == null ? "" : wordResult.errorCode() + " ")
                                + wordResult.errorMessage());
                    }
                }
            }
            String detail = errors.isEmpty() ? "" : "（例: " + String.join(" / ", errors) + "）";
            if (succeeded == 0) {
                throw new StepException("日本語単語 " + kind + " AI取得: " + list.size() + " 語すべて失敗しました。"
                        + detail);
            }
            return result("成功 " + succeeded + " / 失敗 " + failed + detail,
                    list.size(), succeeded, failed, 0, startedMs);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    /** 待って結果を取る（並列に投げた 1 語ぶん）。 */
    private static WordResult await(Future<WordResult> future) {
        try {
            return future.get();
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            return new WordResult("", false, "INTERRUPTED", "中断されました。");
        } catch (ExecutionException cause) {
            Throwable reason = cause.getCause() == null ? cause : cause.getCause();
            return new WordResult("", false, "UNEXPECTED",
                    reason.getMessage() == null ? "不明なエラー" : reason.getMessage());
        }
    }

    /** 1 語ぶんを処理する（内容種別ごとに AI を呼び、成功したものだけ書く）。 */
    private WordResult processWord(BatchExecutionEntity execution, String batchCode, String kind,
                                   List<String> contentTypes, JapaneseWordAiMapper.AiCallTarget target,
                                   JapaneseWordAiSettings settings,
                                   GeometryAiConnectionResolver.AiConnection connection) {
        int succeeded = 0;
        String firstErrorCode = null;
        String firstErrorMessage = null;
        for (String contentType : contentTypes) {
            Attempt attempt = attemptOf(execution, batchCode, kind, target, contentType, settings, connection);
            if (attempt.succeeded()) {
                succeeded += 1;
            } else if (firstErrorCode == null) {
                firstErrorCode = attempt.errorCode();
                firstErrorMessage = attempt.errorMessage();
            }
        }
        boolean allSucceeded = succeeded == contentTypes.size();
        if (!allSucceeded) {
            log.warn("japanese word ai failed. wordId={} kind={} succeeded={}/{} errorCode={} reason={}",
                    target.getWordId(), kind, succeeded, contentTypes.size(), firstErrorCode, firstErrorMessage);
        }
        return new WordResult(target.getHeading(), allSucceeded, firstErrorCode,
                firstErrorMessage == null ? "失敗しました。" : firstErrorMessage);
    }

    /**
     * 1 つの内容種別を、再試行しながら取る。
     *
     * <p>成功したら内容を書き、失敗したら理由を生成履歴に残す。4xx は再試行しない。</p>
     */
    private Attempt attemptOf(BatchExecutionEntity execution, String batchCode, String kind,
                              JapaneseWordAiMapper.AiCallTarget target, String contentType,
                              JapaneseWordAiSettings settings,
                              GeometryAiConnectionResolver.AiConnection connection) {
        // 実行中の行があれば使う（二重起動を防ぐ）
        Long generationId = mapper.findActiveGeneration(target.getWordId(), contentType);
        int contentVersion;
        if (generationId == null) {
            JapaneseWordAiMapper.GenerationRow row = new JapaneseWordAiMapper.GenerationRow();
            row.setWordId(target.getWordId());
            row.setContentType(contentType);
            row.setAiType(settings.provider());
            row.setModelName(connection.model());
            // この取得の版。問題（C/D/E）の行にも同じ番号を入れて、画面の履歴で版を選べるようにする
            contentVersion = mapper.nextGenerationVersion(target.getWordId(), contentType);
            row.setContentVersion(contentVersion);
            mapper.insertGeneration(row);
            generationId = row.getGenerationId();
        } else {
            // 中断した取得の続きは、**その生成と同じ版**の問題を書く（版を増やさない）
            contentVersion = mapper.findGenerationVersion(generationId);
        }
        return callAndWrite(execution, batchCode, kind, generationId, contentVersion, contentType,
                target, settings, connection);
    }

    /**
     * 1 件（1 語 × 1 内容種別）を、再試行しながら AI に取らせて書く。
     *
     * <p>同期の一括実行（{@link #run}）と非同期の働き手（{@link #runItem}）が**同じ道**を通る。
     * 生成履歴の行は呼び出し側が用意する（同期は「実行中の行を探す／無ければ作る」、
     * 非同期は「受付が積んだ行を確保して使う」）。</p>
     */
    private Attempt callAndWrite(BatchExecutionEntity execution, String batchCode, String kind,
                                 long generationId, int contentVersion, String contentType,
                                 JapaneseWordAiMapper.AiCallTarget target,
                                 JapaneseWordAiSettings settings,
                                 GeometryAiConnectionResolver.AiConnection connection) {
        int attempts = Math.max(0, settings.retryLimit()) + 1;
        String lastErrorCode = null;
        String lastErrorMessage = null;
        for (int attempt = 0; attempt < attempts; attempt += 1) {
            JapaneseWordAiPrompt.Prompt prompt =
                    promptOf(batchCode, contentType, target, settings);
            JapaneseWordAiClient.CallResult call = aiClient.call(new JapaneseWordAiClient.AiCallRequest(
                    batchCode, execution == null ? null : execution.getExecutionId(),
                    "japanese-word/" + target.getWordId() + "/" + contentType + "/attempt-" + (attempt + 1),
                    target.getHeading(), connection.provider(), connection.model(), connection.url(),
                    connection.apiKey(), prompt.system(), prompt.user(),
                    settings.timeoutSeconds(), settings.temperature(), settings.maxCompletionTokens()));

            long writeStartedMs = System.currentTimeMillis();
            WriteResult written = call.isSuccess()
                    ? write(generationId, contentVersion, contentType, target, call.content(), connection)
                    : WriteResult.failure(call.errorCode(), call.errorMessage());
            int durationMs = (int) (System.currentTimeMillis() - writeStartedMs);

            if (written.succeeded()) {
                // 呼出履歴の ID を生成履歴へ渡す（切片3 で直した不具合: 以前は常に null だった）
                mapper.markGenerationSucceeded(generationId, call.callLogId(), written.generatedCount(), durationMs);
                return Attempt.success();
            }
            mapper.markGenerationFailed(generationId, written.errorCode(), written.errorMessage(), durationMs);
            lastErrorCode = written.errorCode();
            lastErrorMessage = written.errorMessage();

            boolean retryable = call.isSuccess() ? written.retryable() : !call.isFatal();
            if (!retryable || attempt + 1 >= attempts) {
                break;
            }
            sleepBackoff(attempt);
        }
        return Attempt.failure(lastErrorCode, lastErrorMessage);
    }

    /**
     * 働き手（非同期）が実行する <b>1 件</b>（受付が積んだ 1 語 × 1 内容種別の行）。
     *
     * <p>同期の {@link #run} との違い:</p>
     * <ul>
     *   <li><b>対象の選定をしない</b>（{@code findTargets} を通さない）。受付が選んだ語をそのまま実行するので、
     *       一度成功した語の取り直し（「すべて再取得」）も成立する</li>
     *   <li>生成履歴の行は**受付が作ったものをそのまま使う**（{@code 内容版数} も受付が決めた値）</li>
     *   <li>失敗しても<b>例外にしない</b>（働き手を止めない。理由は生成履歴の
     *       {@code FAILED} と実行ログに残る）</li>
     * </ul>
     *
     * @return 成功したか（false は失敗。理由は生成履歴に入っている）
     */
    public boolean runItem(JapaneseWordAiMapper.GenerationRow item) {
        String contentType = item.getContentType();
        String batchCode;
        String kind;
        JapaneseWordAiSettings settings;
        GeometryAiConnectionResolver.AiConnection connection;
        try {
            batchCode = JapaneseWordAiSettings.batchCodeOf(contentType);
            kind = kindOf(contentType);
            settings = JapaneseWordAiSettings.of(
                    settingsService.requireSettings(batchCode, JapaneseWordAiSettings.requirements(batchCode)),
                    batchCode);
            connection = connectionResolver.resolve(batchCode, settings.provider());
        } catch (RuntimeException cause) {
            // 設定が欠けている・接続が解決できない等。**実行中のまま残さない**:
            // 残すと働き手が STALE_MINUTES ごとに拾い直し、直らないまま回り続ける
            // （AI 生図の「工程が想定外で止まったら要求行へ FAILED と理由を書く」と同じ規則）
            mapper.markGenerationFailed(item.getGenerationId(), "CONFIG_ERROR",
                    cause.getMessage() == null ? "設定を解決できませんでした。" : cause.getMessage(), 0);
            return false;
        }

        JapaneseWordAiMapper.AiCallTarget target = mapper.findWordTarget(item.getWordId());
        if (target == null) {
            // 語が消えた（論理削除）ときに、実行中のまま残さない
            mapper.markGenerationFailed(item.getGenerationId(), "WORD_NOT_FOUND",
                    "単語が見つかりません（削除された可能性があります）。", 0);
            return false;
        }
        // どの AI 区分・モデルで走ったかを残す（受付の時点では接続を解決していない）
        mapper.markGenerationStarted(item.getGenerationId(), connection.provider(), connection.model());

        Attempt attempt = callAndWrite(null, batchCode, kind, item.getGenerationId(),
                item.getContentVersion(), contentType, target, settings, connection);
        return attempt.succeeded();
    }

    /** 内容種別から、AI へ渡す入力を作る（収録は語ごとに引く）。 */
    private JapaneseWordAiPrompt.Prompt promptOf(String batchCode, String contentType,
                                                 JapaneseWordAiMapper.AiCallTarget target,
                                                 JapaneseWordAiSettings settings) {
        List<JapaneseWordAiPrompt.Collection> collections = new ArrayList<>();
        for (JapaneseWordAiMapper.CollectionRow row : mapper.listCollections(target.getWordId())) {
            collections.add(new JapaneseWordAiPrompt.Collection(row.getBook(), row.getCategory(),
                    row.getWordSeq(), row.getPartOfSpeech(), row.getChineseMeaning()));
        }
        Map<String, Object> detail = detailReader.readAssembledDetail(target.getWordId());
        JapaneseWordAiPrompt.WordInput input = new JapaneseWordAiPrompt.WordInput(
                target.getWordId(), target.getHeading(), target.getReading(), target.getPartOfSpeech(),
                target.getJlptLevel(), collections, detail);
        // 設定の System Prompt の後ろに「出力形式（JSON Schema）」を足す（DTO が唯一の定義）。
        // どのバッチかで DTO が決まる（AiResponseDtos に登録済み）
        String systemPrompt = responseFormatPrompt.appendTo(settings.systemPrompt(), batchCode);
        return JapaneseWordAiPrompt.of(systemPrompt, settings.userPrompt(), kindOf(contentType), input);
    }

    /** 呼出履歴のプロンプトに載せる取得区分（内容種別から戻す）。 */
    private static String kindOf(String contentType) {
        return switch (contentType) {
            case "A_DETAIL" -> "DETAIL";
            case "C1_READING", "C2_KANJI" -> "C";
            case "D_CONTEXT_MEANING" -> "D";
            case "E_KANJI_USAGE" -> "E";
            default -> contentType;
        };
    }

    /**
     * 取れた内容を書き込む。
     *
     * @param generationId   今回の AI 生成（詳細の版のヘッダに {@code 生成ID} として入る）
     * @param contentVersion この取得の版（問題の行に入れる。画面の履歴で版を切り替える目印）
     */
    private WriteResult write(Long generationId, int contentVersion, String contentType,
                              JapaneseWordAiMapper.AiCallTarget target,
                              String content, GeometryAiConnectionResolver.AiConnection connection) {
        if (DETAIL_TYPE.equals(contentType)) {
            JapaneseWordAiDtoMapper.DetailResult parsed = JapaneseWordAiDtoMapper.parseDetail(content);
            if (!parsed.isSuccess()) {
                return WriteResult.failure(parsed.errorCode(), parsed.errorMessage());
            }
            // 詳細は「新しい版を作る」（上書きしない）。人が直した行は新しい版へ複製される
            // （JapaneseWordAiDetailWriter がトランザクションの中で行う）
            detailWriter.createVersion(target.getWordId(), parsed.detailJson(), generationId,
                    connection.provider(), connection.model());
            // 画面からの登録では読みを取らない（設計 §5）。詳細を取ったときに読みを埋める。
            // 既に読みがある語は呼ばない（SQL 側でも弾く。画面で入れた値を AI で上書きしない）
            if (parsed.reading() != null && isBlank(target.getReading())) {
                int filled = mapper.updateReadingIfBlank(target.getWordId(), parsed.reading());
                if (filled == 0) {
                    // 同じ見出し語・同じ読みの語が既にある（部分 UNIQUE に当てない）。
                    // 詳細は入っているので失敗にはしない
                    log.warn("japanese word ai reading not filled. wordId={} reading={}",
                            target.getWordId(), parsed.reading());
                }
            }
            // JLPT レベルも同じ理由で AI の値を書き戻す（一覧の絞り込みを効かせる）
            if (parsed.jlpt() != null && isBlank(target.getJlptLevel())
                    && mapper.updateJlptIfBlank(target.getWordId(), parsed.jlpt()) == 0) {
                log.warn("japanese word ai jlpt not filled. wordId={} jlpt={}",
                        target.getWordId(), parsed.jlpt());
            }
            return WriteResult.success(1);
        }
        JapaneseWordAiDtoMapper.ProblemResult parsed = JapaneseWordAiDtoMapper.parseProblems(
                content, kindOf(contentType), target.getHeading(), target.getReading());
        if (!parsed.isSuccess()) {
            return WriteResult.failure(parsed.errorCode(), parsed.errorMessage());
        }
        int written = 0;
        for (JapaneseWordAiDtoMapper.ParsedProblem problem : parsed.problems()) {
            // C の応答（batC42）には C1 と C2 が**両方**入っている。内容種別ごとに AI を
            // 呼ぶので、そのまま書くと 2 回目が 1 回目の題を ARCHIVED にして入れ直してしまう
            // （同じ内容を 2 度書くだけ。生成件数も二重に数える）。ここは今回の内容種別の題だけを書く。
            // D / E の応答は 1 件で、その種別は内容種別と同じなので、この判定は素通りする。
            if (!contentType.equals(problem.questionType())) {
                continue;
            }
            // 同じ語・同じ種別の古い ACTIVE は ARCHIVED にしてから入れる（消さない）
            mapper.archiveQuestions(target.getWordId(), problem.questionType());
            JapaneseWordAiMapper.QuestionRow row = new JapaneseWordAiMapper.QuestionRow();
            row.setWordId(target.getWordId());
            row.setQuestionType(problem.questionType());
            row.setQuestionNo(problem.questionNo());
            row.setQuestionJa(problem.questionJa());
            row.setQuestionZh(problem.questionZh());
            row.setTargetHeading(problem.targetHeading());
            row.setTargetReading(problem.targetReading());
            row.setExampleJa(problem.exampleJa());
            row.setExampleReading(problem.exampleReading());
            row.setAudioText(problem.audioText());
            row.setCorrectValue(problem.correctValue());
            row.setCorrectNote(problem.correctNote());
            row.setExplanationJa(problem.explanationJa());
            row.setExplanationZh(problem.explanationZh());
            row.setDifficulty(problem.difficulty());
            row.setStructuredJson(problem.structuredJson());
            // 生成履歴と同じ版を入れる（画面の履歴で「この版を使う」と切り替えられる）
            row.setContentVersion(contentVersion);
            mapper.insertQuestion(row);
            Long questionId = row.getQuestionId();
            for (JapaneseWordAiDtoMapper.ParsedChoice choice : problem.choices()) {
                JapaneseWordAiMapper.ChoiceRow choiceRow = new JapaneseWordAiMapper.ChoiceRow();
                choiceRow.setQuestionId(questionId == null ? 0L : questionId);
                choiceRow.setOrderNo(choice.orderNo());
                choiceRow.setValue(choice.value());
                choiceRow.setReading(choice.reading());
                choiceRow.setCorrect(choice.correct());
                choiceRow.setWrongType(choice.wrongType());
                choiceRow.setDescriptionJa(choice.explanationJa());
                choiceRow.setDescriptionZh(choice.explanationZh());
                mapper.insertChoice(choiceRow);
            }
            written += 1;
        }
        return WriteResult.success(written);
    }

    /** 読みがまだ無いか（画面からの登録直後は空）。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void sleepBackoff(int attempt) {
        long wait = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
        try {
            Thread.sleep(wait);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        }
    }

    /** 実行の要約（{@code BAT_バッチ実行履歴情報.結果} に入る）。 */
    private static Map<String, Object> result(String message, int targets, int succeeded, int failed,
                                              int skipped, long startedMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "日本語単語 AI取得: " + message);
        result.put("targets", targets);
        result.put("succeeded", succeeded);
        result.put("failed", failed);
        result.put("skipped", skipped);
        result.put("durationMs", System.currentTimeMillis() - startedMs);
        return result;
    }

    /** 1 語ぶんの結果。 */
    private record WordResult(String heading, boolean succeeded, String errorCode, String errorMessage) {
    }

    /** 1 内容種別ぶんの結果。 */
    private record Attempt(boolean succeeded, String errorCode, String errorMessage) {

        static Attempt success() {
            return new Attempt(true, null, null);
        }

        static Attempt failure(String errorCode, String errorMessage) {
            return new Attempt(false, errorCode, errorMessage);
        }
    }

    /** 書き込みの結果。 */
    private record WriteResult(boolean succeeded, int generatedCount, String errorCode, String errorMessage,
                               boolean retryable) {

        static WriteResult success(int generatedCount) {
            return new WriteResult(true, generatedCount, null, null, false);
        }

        /** 内容が読めなかった（再試行する価値がある）。 */
        static WriteResult failure(String errorCode, String errorMessage) {
            return new WriteResult(false, 0, errorCode, errorMessage, true);
        }
    }

    /** 業務上の失敗（実行履歴を FAILED にする）。 */
    public static class StepException extends RuntimeException {

        public StepException(String message) {
            super(message);
        }
    }
}
