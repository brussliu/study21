package com.study21.user.classroom;

import com.study21.common.core.exception.ConflictException;

import java.util.List;

/**
 * 音声の分塊がそろっていないまま終了しようとした（409）。
 *
 * <p>「少なくとも 1 つある」では足りないので、**足りない連番そのもの**を返す: 画面はその
 * 分塊だけを送り直せる（利用者に「どこを直せばよいか」を伝える）。あわせて、**明示の
 * 「不完全なまま終了」**を選べることも画面に伝える（音を黙って失わない）。</p>
 */
public class ChunkChecklistException extends ConflictException {

    private final transient ClassroomModels.ChunkChecklist checklist;

    public ChunkChecklistException(ClassroomModels.ChunkChecklist checklist, String message) {
        super(message);
        this.checklist = checklist;
    }

    /** 足りない連番と、保存できている数（画面の状態表示に使う）。 */
    public ClassroomModels.ChunkChecklist checklist() {
        return checklist;
    }

    /** 足りない連番。 */
    public List<Integer> missingSeqs() {
        return checklist == null ? List.of() : checklist.missingSeqs();
    }
}
