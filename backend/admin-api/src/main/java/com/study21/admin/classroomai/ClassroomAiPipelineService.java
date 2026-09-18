package com.study21.admin.classroomai;

import com.study21.admin.batch.BatchService;
import com.study21.common.core.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 授業ノート生成の**起動の薄い入口**（画面は 1 回だけ呼ぶ）。
 *
 * <p>ノート行（`CR_授業ノート情報`）の生成状態=PENDING を、その種別に応じたバッチ
 * （PHASE→batC61 / FINAL→batC62）で処理する。user-api が作った PENDING 行を
 * `BatchService.rerunStep` で 1 件だけ処理する（`GeometryAiBatchController` 相当）。</p>
 */
@Service
public class ClassroomAiPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiPipelineService.class);

    private static final String OPERATOR_FALLBACK = "classroom-ai";

    private final BatchService batchService;
    private final ClassroomNoteMapper noteMapper;

    public ClassroomAiPipelineService(BatchService batchService, ClassroomNoteMapper noteMapper) {
        this.batchService = batchService;
        this.noteMapper = noteMapper;
    }

    /** 1 件のノート（PENDING）をその種別のバッチで生成する。 */
    public Map<String, Object> run(long noteId, String operator) {
        ClassroomNoteEntity note = noteMapper.findById(noteId);
        if (note == null) {
            throw new NotFoundException("授業ノートが見つかりません: " + noteId);
        }
        String batchCode = "FINAL".equals(note.getKind()) ? "batC62" : "batC61";
        String operatorCode = operator == null || operator.isBlank() ? OPERATOR_FALLBACK : operator.trim();

        Map<String, Object> stepResult = batchService.rerunStep(batchCode, operatorCode, payloadOf(noteId));
        ClassroomNoteEntity saved = noteMapper.findById(noteId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noteId", noteId);
        result.put("kind", note.getKind());
        result.put("batchCode", batchCode);
        result.put("status", saved == null ? null : saved.getStatus());
        result.put("step", stepResult);
        log.info("classroom ai pipeline finished. noteId={} kind={} status={}",
                noteId, note.getKind(), saved == null ? null : saved.getStatus());
        return result;
    }

    private static String payloadOf(long noteId) {
        return "{\"noteId\":" + noteId + "}";
    }
}
