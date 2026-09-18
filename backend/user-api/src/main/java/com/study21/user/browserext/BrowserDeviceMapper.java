package com.study21.user.browserext;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

/**
 * NET_ブラウザ端末情報（拡張が登録した端末）の Mapper。
 */
@Mapper
public interface BrowserDeviceMapper {

    BrowserDeviceEntity findByAccountAndDevice(@Param("accountId") long accountId,
                                               @Param("deviceId") String deviceId);

    /** 画面の端末一覧（接続中のものが先、次に新しい順）。 */
    List<BrowserDeviceEntity> listByAccount(@Param("accountId") long accountId);

    int insert(BrowserDeviceEntity entity);

    /**
     * register による更新（端末の情報を上書きし、最終起動日時と最終心拍日時を進める）。
     */
    int updateOnRegister(@Param("entity") BrowserDeviceEntity entity,
                         @Param("heartbeatAt") Timestamp heartbeatAt,
                         @Param("version") int version);

    /** heartbeat（最終心拍日時だけ進める）。 */
    int touchHeartbeat(@Param("accountId") long accountId,
                       @Param("deviceId") String deviceId,
                       @Param("heartbeatAt") Timestamp heartbeatAt,
                       @Param("version") int version);

    /** events の受信（最終送信日時を進め、バッチに入っていた端末情報で空欄を埋める）。 */
    int touchSent(@Param("entity") BrowserDeviceEntity entity,
                  @Param("sentAt") Timestamp sentAt,
                  @Param("version") int version);
}
