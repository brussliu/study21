package com.study21.admin.studymonitor;

/**
 * MON_学習モニターカメラ情報 の 1 行（batL02 が使う列だけを持つ）。
 *
 * <p>2.0 は「ユーザーID + カメラコード」で 1 台を特定していたが、2.1 はカメラが 1 台だけなので
 * **カメラコード**（＝ソースフォルダー名）で一意に決める（uq_mon_camera_code）。</p>
 */
public class StudyMonitorCameraEntity {

    /** カメラコード（ソースフォルダー名。例: XiaomiCamera_00_B88880D0F03E）。 */
    private String cameraCode;
    /** カメラ名称。 */
    private String cameraName;
    /** 設置場所（設定 STUDY_MONITOR_CAMERA_LOCATION）。 */
    private String location;
    /** スナップショットの切出間隔（秒。設定 STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS）。 */
    private Integer snapshotIntervalSeconds;

    public String getCameraCode() {
        return cameraCode;
    }

    public void setCameraCode(String cameraCode) {
        this.cameraCode = cameraCode;
    }

    public String getCameraName() {
        return cameraName;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getSnapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public void setSnapshotIntervalSeconds(Integer snapshotIntervalSeconds) {
        this.snapshotIntervalSeconds = snapshotIntervalSeconds;
    }
}
