package com.study21.admin.classroomai;

import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 授業録音（音声 + 転写）のクリーンアップ。**既存の batR02 に相乗りする**
 * （新しいバッチを作らない。設計 §10）。
 *
 * <p>保持日数は設定 `CLASSROOM_AI_RETENTION_DAYS`（既定 30 日）。**記録行と転写は残し、
 * 音声ファイルだけ消す**（履歴は監査として残す）。消したことは音声の参照列を NULL にして記録する。
 * 設定が未投入のときは削除を見送る（他のクリーンアップを止めないため）。</p>
 */
@Component
public class ClassroomRecordingCleanup {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRecordingCleanup.class);

    private static final String TASK_CODE = "batR02";
    private static final int BATCH_LIMIT = 200;

    private final ClassroomRecordMapper recordMapper;
    private final ClassroomRecordingChunkMapper chunkMapper;
    private final ClassroomRecordingStorage storage;
    private final SettingsService settingsService;

    public ClassroomRecordingCleanup(ClassroomRecordMapper recordMapper,
                                     ClassroomRecordingChunkMapper chunkMapper,
                                     ClassroomRecordingStorage storage,
                                     SettingsService settingsService) {
        this.recordMapper = recordMapper;
        this.chunkMapper = chunkMapper;
        this.storage = storage;
        this.settingsService = settingsService;
    }

    /** 保持日数を過ぎた録音の音声ファイルを消す。 */
    @Transactional
    public Map<String, Object> run() {
        int retentionDays = retentionDays();
        Map<String, Object> result = new LinkedHashMap<>();
        if (retentionDays <= 0) {
            result.put("skipped", true);
            result.put("message", "授業録音の削除は行いませんでした（保持日数の設定が未投入です）。");
            return result;
        }

        List<ClassroomRecordEntity> targets = recordMapper.findRetentionTargets(retentionDays, BATCH_LIMIT);
        int deletedFiles = 0;
        int deletedChunks = 0;
        int clearedRows = 0;
        for (ClassroomRecordEntity target : targets) {
            if (storage.delete(target.getAudioPath(), target.getAudioName())) {
                deletedFiles += 1;
            }
            /*
             * 分塊（`chunk-{記録ID}-*.webm`）も消す。再生用の 1 本は分塊から作られるので、
             * 分塊を残すとディスクだけが太る（1 授業で数十 MB）。実体を消したら行も消す
             * （「保存済み」に見えると、user-api の冪等判定が実体の無い分塊を在ると見なす）。
             */
            deletedChunks += storage.deleteChunks(target.getAudioPath(), target.getRecordId());
            chunkMapper.deleteByRecord(target.getRecordId());
            recordMapper.clearAudioFiles(target.getRecordId());
            clearedRows += 1;
        }
        result.put("retentionDays", retentionDays);
        result.put("targets", targets.size());
        result.put("deletedFiles", deletedFiles);
        result.put("deletedChunks", deletedChunks);
        result.put("clearedRows", clearedRows);
        result.put("message", "授業録音の音声を削除しました。（対象 " + targets.size() + " 件 / うちファイルあり "
                + deletedFiles + " 件・分塊 " + deletedChunks + " 件・保持 " + retentionDays + " 日）");
        log.info("classroom recording cleanup finished. retentionDays={} targets={} deleted={} chunks={} rows={}",
                retentionDays, targets.size(), deletedFiles, deletedChunks, clearedRows);
        return result;
    }

    private int retentionDays() {
        try {
            return Integer.parseInt(settingsService
                    .requireGlobal(TASK_CODE, "CLASSROOM_AI", "CLASSROOM_AI_RETENTION_DAYS").trim());
        } catch (SettingsValidationException | NumberFormatException cause) {
            log.warn("授業録音の保持日数（CLASSROOM_AI_RETENTION_DAYS）を読めないため、削除を見送ります。");
            return 0;
        }
    }
}
