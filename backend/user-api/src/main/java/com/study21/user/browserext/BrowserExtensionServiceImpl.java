package com.study21.user.browserext;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.security.exception.UnauthenticatedException;
import com.study21.user.net.WebBrowsingLogMapper;
import com.study21.user.net.WebBrowsingLogModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * ブラウザ拡張との連携の実装。
 *
 * <p>2.0 の受信 API（`/api/browserHistory/registerDevice|heartbeat|batchSave`）を 2.1 の
 * テーブル（NET_ブラウザ接続情報 / NET_ブラウザ端末情報 / NET_Web閲覧履歴情報）に合わせて
 * 作り直したもの。2.0 との違い:</p>
 * <ul>
 *   <li>認証がある。拡張は接続コード（64 文字の 16 進）を送り、そこから持ち主が決まる
 *       （2.0 はリクエストの userId をそのまま信じていた）。</li>
 *   <li>イベントには拡張が付けた UUID が入り、再送されても二重登録しない。</li>
 *   <li>端末の登録は upsert（2.0 は null で上書きして OS種別 などが消えることがあった）。</li>
 * </ul>
 */
@Service
public class BrowserExtensionServiceImpl implements BrowserExtensionService {

    private static final Logger log = LoggerFactory.getLogger(BrowserExtensionServiceImpl.class);

    /** 接続コードの元になる乱数（32 バイト = 16 進 64 文字）。 */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BrowserConnectionMapper connectionMapper;
    private final BrowserDeviceMapper deviceMapper;
    private final WebBrowsingLogMapper webBrowsingLogMapper;

    public BrowserExtensionServiceImpl(BrowserConnectionMapper connectionMapper,
                                       BrowserDeviceMapper deviceMapper,
                                       WebBrowsingLogMapper webBrowsingLogMapper) {
        this.connectionMapper = connectionMapper;
        this.deviceMapper = deviceMapper;
        this.webBrowsingLogMapper = webBrowsingLogMapper;
    }

    // ------------------------------------------------------------------ 画面向け

    @Override
    @Transactional(readOnly = true)
    public BrowserExtensionModels.RegistrationView registration(long accountId) {
        return buildView(connectionMapper.findByAccountId(accountId), null);
    }

    @Override
    @Transactional
    public BrowserExtensionModels.RegistrationView issueToken(long accountId) {
        BrowserConnectionEntity existing = connectionMapper.findByAccountId(accountId);
        if (existing != null) {
            return buildView(existing, "接続コードは発行済みです。");
        }
        BrowserConnectionEntity entity = new BrowserConnectionEntity();
        entity.setAccountId(accountId);
        entity.setToken(newToken());
        connectionMapper.insert(entity);
        log.info("ブラウザ拡張の接続コードを発行しました。accountId={}", accountId);
        return buildView(connectionMapper.findByAccountId(accountId), "接続コードを発行しました。");
    }

    @Override
    @Transactional
    public BrowserExtensionModels.RegistrationView reissueToken(long accountId) {
        BrowserConnectionEntity existing = connectionMapper.findByAccountId(accountId);
        if (existing == null) {
            return issueToken(accountId);
        }
        String token = newToken();
        int updated = connectionMapper.updateToken(existing.getConnectionId(), token, accountId, existing.getVersion());
        if (updated == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        log.info("ブラウザ拡張の接続コードを再発行しました。accountId={}", accountId);
        return buildView(connectionMapper.findByAccountId(accountId),
                "接続コードを再発行しました。以前のコードは使えなくなります。");
    }

    // ------------------------------------------------------------ 拡張からの受信

    @Override
    @Transactional
    public BrowserExtensionModels.RegisterResult register(String token,
                                                          BrowserExtensionModels.RegisterRequest request) {
        BrowserConnectionEntity connection = resolveConnection(token);
        long accountId = connection.getAccountId();
        String deviceId = request.deviceId().trim();
        Timestamp now = now();
        Timestamp registeredAt = parseTimestamp(request.registeredAt());

        BrowserDeviceEntity entity = new BrowserDeviceEntity();
        entity.setAccountId(accountId);
        entity.setDeviceId(deviceId);
        entity.setDeviceName(blankToNull(request.deviceName()));
        entity.setOsType(blankToNull(request.osType()));
        entity.setBrowserType(blankToNull(request.browserType()));
        entity.setBrowserVersion(blankToNull(request.browserVersion()));
        entity.setExtensionVersion(blankToNull(request.extensionVersion()));
        entity.setProfileId(blankToNull(request.profileId()));
        entity.setLastStartedAt(registeredAt == null ? now : registeredAt);

        BrowserDeviceEntity existing = deviceMapper.findByAccountAndDevice(accountId, deviceId);
        if (existing == null) {
            entity.setLastHeartbeatAt(now);
            deviceMapper.insert(entity);
        } else {
            if (deviceMapper.updateOnRegister(entity, now, existing.getVersion()) == 0) {
                throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
            }
        }
        connectionMapper.touch(connection.getConnectionId(), now);
        log.info("ブラウザ拡張の端末を登録しました。accountId={}, deviceId={}, new={}", accountId, deviceId,
                existing == null);
        return new BrowserExtensionModels.RegisterResult(deviceId, entity.getDeviceName(), "端末を登録しました。");
    }

    @Override
    @Transactional
    public BrowserExtensionModels.HeartbeatResult heartbeat(String token,
                                                            BrowserExtensionModels.HeartbeatRequest request) {
        BrowserConnectionEntity connection = resolveConnection(token);
        long accountId = connection.getAccountId();
        String deviceId = request.deviceId().trim();
        Timestamp at = parseTimestamp(request.heartbeatAt());
        if (at == null) {
            at = now();
        }

        BrowserDeviceEntity existing = deviceMapper.findByAccountAndDevice(accountId, deviceId);
        if (existing == null) {
            // 拡張を入れ直した直後など、register より先に心拍が届くことがある。
            // 端末の行を作っておけば、次の register / events で情報が埋まる。
            BrowserDeviceEntity entity = new BrowserDeviceEntity();
            entity.setAccountId(accountId);
            entity.setDeviceId(deviceId);
            entity.setLastHeartbeatAt(at);
            deviceMapper.insert(entity);
            log.info("ブラウザ拡張の端末を心拍から登録しました。accountId={}, deviceId={}", accountId, deviceId);
        } else if (deviceMapper.touchHeartbeat(accountId, deviceId, at, existing.getVersion()) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        connectionMapper.touch(connection.getConnectionId(), at);
        return new BrowserExtensionModels.HeartbeatResult(deviceId, at.toString(), "端末の状態を更新しました。");
    }

    @Override
    @Transactional
    public BrowserExtensionModels.BatchSaveResult saveEvents(String token,
                                                             BrowserExtensionModels.EventBatchRequest request) {
        BrowserConnectionEntity connection = resolveConnection(token);
        long accountId = connection.getAccountId();
        String deviceId = request.deviceId().trim();
        Timestamp now = now();
        Timestamp sentAt = parseTimestamp(request.sentAt());
        if (sentAt == null) {
            sentAt = now;
        }

        BrowserDeviceEntity existing = deviceMapper.findByAccountAndDevice(accountId, deviceId);
        String deviceName = firstNonBlank(request.deviceName(), existing == null ? null : existing.getDeviceName());
        String browserType = firstNonBlank(request.browserType(), existing == null ? null : existing.getBrowserType());
        String profileId = firstNonBlank(request.profileId(), existing == null ? null : existing.getProfileId());

        BrowserDeviceEntity entity = new BrowserDeviceEntity();
        entity.setAccountId(accountId);
        entity.setDeviceId(deviceId);
        entity.setDeviceName(deviceName);
        entity.setBrowserType(browserType);
        entity.setBrowserVersion(request.browserVersion());
        entity.setExtensionVersion(request.extensionVersion());
        entity.setProfileId(profileId);
        if (existing == null) {
            entity.setLastHeartbeatAt(now);
            deviceMapper.insert(entity);
        } else {
            deviceMapper.touchSent(entity, sentAt, existing.getVersion());
        }

        List<WebBrowsingLogModels.NewEvent> rows = new ArrayList<>();
        int skipped = 0;
        for (BrowserExtensionModels.EventItem item : request.events()) {
            String eventType = item.eventType() == null ? null : item.eventType().trim().toUpperCase();
            if (eventType == null || !BrowserExtensionModels.EVENT_TYPES.contains(eventType)) {
                // 知らないイベント種別でバッチ全体を止めない（拡張のキューが詰まってしまうため）
                skipped += 1;
                continue;
            }
            Timestamp visitedAt = parseTimestamp(item.visitedAt());
            rows.add(new WebBrowsingLogModels.NewEvent(
                    blankToNull(item.clientEventId()),
                    eventType,
                    blankToNull(item.url()),
                    blankToNull(item.domain()),
                    blankToNull(item.pageTitle()),
                    blankToNull(item.referrerUrl()),
                    blankToNull(item.faviconUrl()),
                    blankToNull(item.transitionType()),
                    item.tabId(),
                    item.windowId(),
                    blankToNull(item.sessionId()),
                    flag(item.active()),
                    flag(item.fromHistorySync()),
                    visitedAt == null ? now : visitedAt,
                    nonNegative(item.staySeconds()),
                    nonNegative(item.visitCount())));
        }

        int inserted = rows.isEmpty()
                ? 0
                : webBrowsingLogMapper.insertBatch(accountId, deviceId, deviceName, browserType, profileId, rows);
        connectionMapper.touch(connection.getConnectionId(), now);

        int received = request.events().size();
        int duplicate = received - inserted - skipped;
        log.info("ブラウザ拡張から閲覧イベントを受信しました。accountId={}, deviceId={}, received={}, inserted={}, duplicate={}, skipped={}",
                accountId, deviceId, received, inserted, duplicate, skipped);
        return new BrowserExtensionModels.BatchSaveResult(received, inserted, duplicate, skipped,
                inserted + " 件を登録しました。");
    }

    // -------------------------------------------------------------------- 内部

    /**
     * 接続コードから持ち主を特定する。
     *
     * <p>2.1 の受信 API で唯一の認証。コードを知らない相手はアカウントを特定できない。</p>
     */
    private BrowserConnectionEntity resolveConnection(String token) {
        String value = blankToNull(token);
        if (value == null) {
            throw new UnauthenticatedException("接続コードが指定されていません。");
        }
        BrowserConnectionEntity connection = connectionMapper.findActiveByToken(value);
        if (connection == null) {
            throw new UnauthenticatedException("接続コードが正しくありません。");
        }
        return connection;
    }

    private BrowserExtensionModels.RegistrationView buildView(BrowserConnectionEntity connection, String message) {
        if (connection == null) {
            return new BrowserExtensionModels.RegistrationView(false, null, 0, 0, List.of(),
                    message == null ? "接続コードはまだ発行されていません。" : message);
        }
        Timestamp onlineAfter = Timestamp.valueOf(LocalDateTime.now()
                .minusMinutes(BrowserExtensionModels.ONLINE_WINDOW_MINUTES));
        List<BrowserExtensionModels.DeviceRow> rows = deviceMapper.listByAccount(connection.getAccountId()).stream()
                .map(entity -> toRow(entity, onlineAfter))
                .toList();
        long online = rows.stream().filter(BrowserExtensionModels.DeviceRow::online).count();
        return new BrowserExtensionModels.RegistrationView(true, connection.getToken(), rows.size(), (int) online,
                rows, message);
    }

    private static BrowserExtensionModels.DeviceRow toRow(BrowserDeviceEntity entity, Timestamp onlineAfter) {
        Timestamp heartbeat = entity.getLastHeartbeatAt();
        boolean online = heartbeat != null && !heartbeat.before(onlineAfter);
        return new BrowserExtensionModels.DeviceRow(
                entity.getRegistrationId() == null ? 0L : entity.getRegistrationId(),
                entity.getDeviceId(),
                entity.getDeviceName(),
                entity.getOsType(),
                entity.getBrowserType(),
                entity.getBrowserVersion(),
                entity.getExtensionVersion(),
                entity.getProfileId(),
                entity.getStatus() == null ? BrowserExtensionModels.STATUS_ACTIVE : entity.getStatus(),
                online,
                iso(entity.getLastStartedAt()),
                iso(entity.getLastSentAt()),
                iso(entity.getLastHeartbeatAt()),
                iso(entity.getCreatedAt()));
    }

    /** 接続コードを作る。人の目で写せる長さに収まる 16 進 64 文字。 */
    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toString();
    }

    /**
     * 拡張が送ってくる ISO 8601 の時刻を読む。
     * 読めない値は null（呼び出し側で受信時刻に置き換える）。
     */
    private static Timestamp parseTimestamp(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(text).toInstant());
        } catch (RuntimeException ignored) {
            // オフセット無し・Instant 形式を順に試す
        }
        try {
            return Timestamp.from(Instant.parse(text));
        } catch (RuntimeException ignored) {
            // ローカル日時として解釈する
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(text));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** '1' / '0' のフラグへ（null は '0'）。 */
    private static String flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? "1" : "0";
    }

    private static Integer nonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        String value = blankToNull(first);
        return value == null ? blankToNull(second) : value;
    }
}
