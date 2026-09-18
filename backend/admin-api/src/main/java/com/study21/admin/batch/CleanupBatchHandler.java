package com.study21.admin.batch;

import com.study21.admin.classroomai.ClassroomRecordingCleanup;
import com.study21.admin.geometryai.GeometryAiImageCleanup;
import org.springframework.stereotype.Component;

/**
 * batR02「バッチ実行履歴・上網履歴クリーンアップ処理」の業務処理。
 *
 * <p>2.1 で実装したのは **AI 生図の画像（保持日数を過ぎたもの）の削除**と
 * **授業録音の音声（保持日数を過ぎたもの）の削除**。設計のとおり新しいバッチを増やさず、
 * 既存の定義と名前（定時クリーンアップ）に相乗りする。</p>
 *
 * <p>実行履歴・上網履歴そのものの削除は今後の実装（定義・名前はその用途のまま残す）。
 * 判断の材料として、AI 生図の要求行・授業記録の行は**消さない**
 * （監査として残し、ファイルだけを消す）。</p>
 */
@Component
public class CleanupBatchHandler implements BatchTaskHandler {

    /** 2.0 から引き継いだコード（種別 R = 定時）。 */
    public static final String BATCH_CODE = "batR02";

    private final GeometryAiImageCleanup imageCleanup;
    private final ClassroomRecordingCleanup recordingCleanup;

    public CleanupBatchHandler(GeometryAiImageCleanup imageCleanup,
                               ClassroomRecordingCleanup recordingCleanup) {
        this.imageCleanup = imageCleanup;
        this.recordingCleanup = recordingCleanup;
    }

    @Override
    public String taskCode() {
        return BATCH_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) {
        String imageMessage = String.valueOf(imageCleanup.run().get("message"));
        String recordingMessage = String.valueOf(recordingCleanup.run().get("message"));
        return imageMessage + " " + recordingMessage;
    }
}
