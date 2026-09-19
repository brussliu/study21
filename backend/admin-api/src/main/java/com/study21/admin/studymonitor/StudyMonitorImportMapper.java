package com.study21.admin.studymonitor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学習状況モニターの取込（batL02）が使う Mapper。
 *
 * <p>2.0 の BatL02Task は DataSource から手で JDBC を叩いていたが、2.1 の規約で
 * MyBatis（{@code @Mapper} + XML）に置き換えた。SQL は
 * {@code resources/mapper/StudyMonitorImportMapper.xml}。DB 操作ログは
 * {@code SqlLoggingInterceptor} が自動で記録する（手書きの SQL ログは書かない）。</p>
 *
 * <p>アカウントID（登録者/更新者）はバッチのため NULL、登録元/更新元コードは 'BAT_L02' を
 * SQL 側で固定する。</p>
 */
@Mapper
public interface StudyMonitorImportMapper {

    /**
     * カメラを upsert する（カメラコードで一意）。
     *
     * <p>既存行の {@code アカウントID}・{@code 旧ユーザーID} には触らない
     * （2.0 の持ち主の情報を壊さない）。</p>
     */
    int upsertCamera(StudyMonitorCameraEntity camera);

    /** カメラID を引く（upsert 直後の採番結果の取得）。 */
    Long findCameraId(@Param("cameraCode") String cameraCode);

    /** 同じカメラで同じ動画ファイル名を取り込み済みか（既取込は飛ばす）。 */
    boolean existsVideo(@Param("cameraId") long cameraId, @Param("fileName") String fileName);

    /** 動画を 1 件 INSERT する（動画ID は採番して entity に返す）。 */
    int insertVideo(StudyMonitorVideoEntity video);

    /**
     * スナップショットを一括 INSERT する。
     * 同じ動画の同じ位置が既にあれば何もしない（2.0 と同じ
     * {@code ON CONFLICT ("動画ID","動画内オフセットミリ秒") DO NOTHING}）。
     *
     * @return 実際に挿入された件数
     */
    int insertSnapshots(@Param("list") List<StudyMonitorSnapshotEntity> snapshots);
}
