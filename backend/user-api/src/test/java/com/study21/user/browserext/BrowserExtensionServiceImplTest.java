package com.study21.user.browserext;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.security.exception.UnauthenticatedException;
import com.study21.user.net.WebBrowsingLogMapper;
import com.study21.user.net.WebBrowsingLogModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ブラウザ拡張との連携の業務ルール（認証・接続コード・イベント登録）。
 *
 * 接続コードは 2.1 で唯一の認証なので、コードが無い・違う場合は必ず 401 にし、
 * リクエストの端末識別子だけでは持ち主を決めない。
 */
class BrowserExtensionServiceImplTest {

    private static final long ACCOUNT_ID = 2L;
    private static final long CONNECTION_ID = 7L;
    private static final String TOKEN = "0123456789abcdef".repeat(4);
    private static final String DEVICE_ID = "chrome-1750000000000-ab12cd34";

    private BrowserConnectionMapper connectionMapper;
    private BrowserDeviceMapper deviceMapper;
    private WebBrowsingLogMapper webBrowsingLogMapper;
    private BrowserExtensionServiceImpl service;

    @BeforeEach
    void setUp() {
        connectionMapper = mock(BrowserConnectionMapper.class);
        deviceMapper = mock(BrowserDeviceMapper.class);
        webBrowsingLogMapper = mock(WebBrowsingLogMapper.class);
        service = new BrowserExtensionServiceImpl(connectionMapper, deviceMapper, webBrowsingLogMapper);
    }

    // ------------------------------------------------------------------ 接続コード

    @Test
    void issuesTokenWhenNoneExists() {
        when(connectionMapper.findByAccountId(ACCOUNT_ID)).thenReturn(null, connection());

        BrowserExtensionModels.RegistrationView view = service.issueToken(ACCOUNT_ID);

        ArgumentCaptor<BrowserConnectionEntity> captor = ArgumentCaptor.forClass(BrowserConnectionEntity.class);
        verify(connectionMapper).insert(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(captor.getValue().getToken()).matches("[0-9a-f]{64}");
        assertThat(view.issued()).isTrue();
        assertThat(view.message()).contains("発行");
    }

    @Test
    void keepsExistingTokenWhenAlreadyIssued() {
        when(connectionMapper.findByAccountId(ACCOUNT_ID)).thenReturn(connection());
        when(deviceMapper.listByAccount(ACCOUNT_ID)).thenReturn(List.of());

        BrowserExtensionModels.RegistrationView view = service.issueToken(ACCOUNT_ID);

        verify(connectionMapper, never()).insert(any());
        assertThat(view.token()).isEqualTo(TOKEN);
        assertThat(view.message()).contains("発行済み");
    }

    @Test
    void reissueReplacesToken() {
        when(connectionMapper.findByAccountId(ACCOUNT_ID)).thenReturn(connection());
        when(connectionMapper.updateToken(anyLong(), any(), any(), anyInt())).thenReturn(1);
        when(deviceMapper.listByAccount(ACCOUNT_ID)).thenReturn(List.of());

        service.reissueToken(ACCOUNT_ID);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(connectionMapper).updateToken(eq(CONNECTION_ID), token.capture(), eq(ACCOUNT_ID), eq(1));
        assertThat(token.getValue()).matches("[0-9a-f]{64}").isNotEqualTo(TOKEN);
    }

    @Test
    void reissueConflictsWhenVersionIsStale() {
        when(connectionMapper.findByAccountId(ACCOUNT_ID)).thenReturn(connection());
        when(connectionMapper.updateToken(anyLong(), any(), any(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> service.reissueToken(ACCOUNT_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsRequestWithoutToken() {
        assertThatThrownBy(() -> service.register(null, registerRequest()))
                .isInstanceOf(UnauthenticatedException.class);
        verifyNoInteractions(deviceMapper);
    }

    @Test
    void rejectsRequestWithUnknownToken() {
        when(connectionMapper.findActiveByToken("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> service.heartbeat("bad-token", new BrowserExtensionModels.HeartbeatRequest(DEVICE_ID, null)))
                .isInstanceOf(UnauthenticatedException.class);
        verifyNoInteractions(deviceMapper);
    }

    // ---------------------------------------------------------------------- 端末

    @Test
    void registersNewDeviceWithOwnerFromToken() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(null);

        BrowserExtensionModels.RegisterResult result = service.register(TOKEN, registerRequest());

        ArgumentCaptor<BrowserDeviceEntity> captor = ArgumentCaptor.forClass(BrowserDeviceEntity.class);
        verify(deviceMapper).insert(captor.capture());
        BrowserDeviceEntity saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(saved.getDeviceName()).isEqualTo("LIU-PC");
        assertThat(saved.getLastHeartbeatAt()).isNotNull();
        assertThat(saved.getLastStartedAt()).isNotNull();
        assertThat(result.deviceId()).isEqualTo(DEVICE_ID);
        verify(connectionMapper).touch(eq(CONNECTION_ID), any());
    }

    @Test
    void updatesExistingDeviceWithOptimisticLock() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(device(4));
        when(deviceMapper.updateOnRegister(any(), any(), anyInt())).thenReturn(1);

        service.register(TOKEN, registerRequest());

        verify(deviceMapper, never()).insert(any());
        verify(deviceMapper).updateOnRegister(any(), any(), eq(4));
    }

    @Test
    void heartbeatRegistersUnknownDevice() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(null);

        service.heartbeat(TOKEN, new BrowserExtensionModels.HeartbeatRequest(DEVICE_ID, "2026-09-13T21:00:00+09:00"));

        ArgumentCaptor<BrowserDeviceEntity> captor = ArgumentCaptor.forClass(BrowserDeviceEntity.class);
        verify(deviceMapper).insert(captor.capture());
        assertThat(captor.getValue().getLastHeartbeatAt()).isNotNull();
    }

    @Test
    void heartbeatTouchesExistingDevice() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(device(2));
        when(deviceMapper.touchHeartbeat(anyLong(), any(), any(), anyInt())).thenReturn(1);

        service.heartbeat(TOKEN, new BrowserExtensionModels.HeartbeatRequest(DEVICE_ID, null));

        verify(deviceMapper).touchHeartbeat(eq(ACCOUNT_ID), eq(DEVICE_ID), any(), eq(2));
    }

    // ------------------------------------------------------------------ イベント

    @Test
    void savesEventsAndTouchesDeviceAndConnection() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(null);
        when(webBrowsingLogMapper.insertBatch(anyLong(), any(), any(), any(), any(), anyList())).thenReturn(2);

        BrowserExtensionModels.BatchSaveResult result = service.saveEvents(TOKEN, batchRequest(
                event("HISTORY_VISITED", "https://example.com/a"),
                event("TAB_UPDATED", "https://example.com/b")));

        assertThat(result.receivedCount()).isEqualTo(2);
        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.duplicateCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        verify(connectionMapper).touch(eq(CONNECTION_ID), any());
    }

    @Test
    void countsResentEventsAsDuplicates() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(device(1));
        // 3 件送ったが、すでに登録済みだったのは 2 件（ON CONFLICT DO NOTHING で 1 件だけ入る）
        when(webBrowsingLogMapper.insertBatch(anyLong(), any(), any(), any(), any(), anyList())).thenReturn(1);

        BrowserExtensionModels.BatchSaveResult result = service.saveEvents(TOKEN, batchRequest(
                event("HISTORY_VISITED", "https://example.com/a"),
                event("HISTORY_VISITED", "https://example.com/a"),
                event("TAB_UPDATED", "https://example.com/b")));

        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(2);
    }

    @Test
    void skipsUnknownEventTypeInsteadOfFailingTheBatch() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(device(1));
        when(webBrowsingLogMapper.insertBatch(anyLong(), any(), any(), any(), any(), anyList())).thenReturn(1);

        BrowserExtensionModels.BatchSaveResult result = service.saveEvents(TOKEN, batchRequest(
                event("HISTORY_VISITED", "https://example.com/a"),
                event("SOMETHING_ELSE", "https://example.com/b")));

        assertThat(result.receivedCount()).isEqualTo(2);
        assertThat(result.insertedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isZero();
    }

    @Test
    void normalizesFlagsTimesAndNegativeNumbers() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(device(1));
        when(webBrowsingLogMapper.insertBatch(anyLong(), any(), any(), any(), any(), anyList())).thenReturn(1);

        BrowserExtensionModels.EventItem item = new BrowserExtensionModels.EventItem(
                "evt-1", "history_visited", "https://example.com/a", "example.com", "ページ",
                "https://example.com/", "https://example.com/f.ico", "link", 12L, 1L, "session-1",
                true, false, "読めない時刻", -5, -1);
        service.saveEvents(TOKEN, batchRequest(item));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WebBrowsingLogModels.NewEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(webBrowsingLogMapper).insertBatch(eq(ACCOUNT_ID), eq(DEVICE_ID), any(), any(), any(),
                captor.capture());
        WebBrowsingLogModels.NewEvent saved = captor.getValue().get(0);
        // イベント種別は大文字に揃える（2.0 の実データと同じ 5 種類）
        assertThat(saved.eventType()).isEqualTo("HISTORY_VISITED");
        assertThat(saved.activeFlag()).isEqualTo("1");
        assertThat(saved.historySyncFlag()).isEqualTo("0");
        assertThat(saved.eventKey()).isEqualTo("evt-1");
        // 読めない時刻は受信時刻で代用する（アクセス日時は NOT NULL）
        assertThat(saved.visitedAt()).isNotNull();
        assertThat(saved.visitedAt().toLocalDateTime()).isBefore(LocalDateTime.now().plusMinutes(1));
        // CHECK 制約（>= 0）に引っかからないよう丸める
        assertThat(saved.staySeconds()).isZero();
        assertThat(saved.viewCount()).isZero();
    }

    @Test
    void readsIsoTimestampWithOffset() {
        when(connectionMapper.findActiveByToken(TOKEN)).thenReturn(connection());
        when(deviceMapper.findByAccountAndDevice(ACCOUNT_ID, DEVICE_ID)).thenReturn(device(1));
        when(webBrowsingLogMapper.insertBatch(anyLong(), any(), any(), any(), any(), anyList())).thenReturn(1);

        BrowserExtensionModels.EventItem item = new BrowserExtensionModels.EventItem(
                "evt-2", "HISTORY_VISITED", "https://example.com/a", "example.com", null,
                null, null, null, null, null, null, false, false, "2026-09-13T21:30:00+09:00", 3, 1);
        service.saveEvents(TOKEN, batchRequest(item));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WebBrowsingLogModels.NewEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(webBrowsingLogMapper).insertBatch(anyLong(), any(), any(), any(), any(), captor.capture());
        Timestamp visitedAt = captor.getValue().get(0).visitedAt();
        assertThat(visitedAt.toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-09-13T21:30:00+09:00").toInstant());
    }

    // -------------------------------------------------------------------- 一覧

    @Test
    void deviceListMarksDevicesWithFreshHeartbeatAsOnline() {
        when(connectionMapper.findByAccountId(ACCOUNT_ID)).thenReturn(connection());
        BrowserDeviceEntity online = device(1);
        online.setDeviceId("chrome-online");
        online.setLastHeartbeatAt(Timestamp.valueOf(LocalDateTime.now()));
        BrowserDeviceEntity offline = device(1);
        offline.setDeviceId("chrome-offline");
        offline.setLastHeartbeatAt(Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)));
        when(deviceMapper.listByAccount(ACCOUNT_ID)).thenReturn(List.of(online, offline));

        BrowserExtensionModels.RegistrationView view = service.registration(ACCOUNT_ID);

        assertThat(view.issued()).isTrue();
        assertThat(view.deviceCount()).isEqualTo(2);
        assertThat(view.onlineCount()).isEqualTo(1);
        assertThat(view.devices().get(0).online()).isTrue();
        assertThat(view.devices().get(1).online()).isFalse();
    }

    @Test
    void deviceListIsEmptyBeforeIssuingToken() {
        when(connectionMapper.findByAccountId(ACCOUNT_ID)).thenReturn(null);

        BrowserExtensionModels.RegistrationView view = service.registration(ACCOUNT_ID);

        assertThat(view.issued()).isFalse();
        assertThat(view.token()).isNull();
        assertThat(view.devices()).isEmpty();
    }

    // -------------------------------------------------------------------- 部品

    private BrowserConnectionEntity connection() {
        BrowserConnectionEntity entity = new BrowserConnectionEntity();
        entity.setConnectionId(CONNECTION_ID);
        entity.setAccountId(ACCOUNT_ID);
        entity.setToken(TOKEN);
        entity.setStatus(BrowserExtensionModels.STATUS_ACTIVE);
        entity.setVersion(1);
        return entity;
    }

    private BrowserDeviceEntity device(int version) {
        BrowserDeviceEntity entity = new BrowserDeviceEntity();
        entity.setRegistrationId(11L);
        entity.setAccountId(ACCOUNT_ID);
        entity.setDeviceId(DEVICE_ID);
        entity.setDeviceName("LIU-PC");
        entity.setStatus(BrowserExtensionModels.STATUS_ACTIVE);
        entity.setVersion(version);
        return entity;
    }

    private BrowserExtensionModels.RegisterRequest registerRequest() {
        return new BrowserExtensionModels.RegisterRequest(DEVICE_ID, "LIU-PC", "Windows", "Chrome",
                "131.0.0.0", "2.1.0", "Default", "2026-09-13T20:00:00+09:00");
    }

    private BrowserExtensionModels.EventBatchRequest batchRequest(BrowserExtensionModels.EventItem... items) {
        return new BrowserExtensionModels.EventBatchRequest(DEVICE_ID, "LIU-PC", "Chrome", "131.0.0.0",
                "2.1.0", "Default", "2026-09-13T21:00:00+09:00", List.of(items));
    }

    private BrowserExtensionModels.EventItem event(String eventType, String url) {
        return new BrowserExtensionModels.EventItem("evt-" + url, eventType, url, "example.com", "ページ",
                null, null, "link", 1L, 1L, "session-1", true, false,
                "2026-09-13T21:00:00+09:00", 5, 1);
    }
}
