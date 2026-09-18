package com.study21.user.browserext;

import com.study21.common.security.exception.UnauthenticatedException;
import com.study21.user.account.AccountService;
import com.study21.user.account.RegisterRequest;
import com.study21.user.account.RegisterResponse;
import com.study21.user.net.WebBrowsingLogModels;
import com.study21.user.net.WebBrowsingLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対するブラウザ拡張の連携の検証。
 *
 * 接続コードの発行 → 端末の登録 → 心拍 → 閲覧イベントの受信 までを
 * サービス層（＝本番と同じ経路）で通し、NET_Web閲覧履歴情報 に実際に入ることを確かめる。
 * テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class BrowserExtensionRepositoryTest {

    private static final String DEVICE_ID = "chrome-test-extension-0001";

    @Autowired
    private BrowserExtensionService browserExtensionService;

    @Autowired
    private AccountService accountService;

    /**
     * 検証用のアカウントをこのテストの中で作る。
     *
     * 実在のアカウント（2 = 生徒など）を使うと、本番で誰かが拡張を繋いだ時点で
     * 「端末が 1 台も無い」という前提が崩れて落ちる（実際に踏んだ）。
     * テストはトランザクションでロールバックするので DB は汚れない。
     */
    private long createStudentAccount() {
        String email = "e2e-ext-test-" + System.nanoTime() + "@example.com";
        RegisterRequest request = new RegisterRequest();
        request.setParentEmail(email);
        request.setParentPassword("Parent1234");
        request.setSei("検証");
        request.setMei("保護者");
        request.setSeiKana("けんしょう");
        request.setMeiKana("ほごしゃ");
        request.setGrade("中学1年生");
        request.setStudentEmail("s-" + email);
        request.setStudentPassword("Student1234");
        request.setAgreed(true);
        RegisterResponse response = accountService.register(request);
        return response.getStudentAccountId();
    }

    @Autowired
    private WebBrowsingLogService webBrowsingLogService;

    @Test
    void issuesTokenRegistersDeviceAndStoresEvents() {
        long accountId = createStudentAccount();

        // 接続コードを発行する
        BrowserExtensionModels.RegistrationView issued = browserExtensionService.issueToken(accountId);
        assertThat(issued.issued()).isTrue();
        assertThat(issued.token()).matches("[0-9a-f]{64}");
        assertThat(issued.devices()).isEmpty();

        // 別のアカウントには影響しない（接続コードは持ち主ごと）
        assertThat(browserExtensionService.registration(createStudentAccount()).issued()).isFalse();

        // 拡張の register
        BrowserExtensionModels.RegisterResult registered = browserExtensionService.register(issued.token(),
                new BrowserExtensionModels.RegisterRequest(DEVICE_ID, "試験用PC", "Windows", "Chrome",
                        "131.0.0.0", "2.1.0", "Default", "2026-09-13T20:00:00+09:00"));
        assertThat(registered.deviceId()).isEqualTo(DEVICE_ID);

        // 心拍（同じ端末が二重に登録されないこと）。時刻は省略＝受信時刻（画面の「接続中」判定に使う）
        browserExtensionService.heartbeat(issued.token(),
                new BrowserExtensionModels.HeartbeatRequest(DEVICE_ID, null));

        BrowserExtensionModels.RegistrationView view = browserExtensionService.registration(accountId);
        assertThat(view.deviceCount()).isEqualTo(1);
        assertThat(view.onlineCount()).isEqualTo(1);
        BrowserExtensionModels.DeviceRow device = view.devices().get(0);
        assertThat(device.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(device.deviceName()).isEqualTo("試験用PC");
        assertThat(device.browserType()).isEqualTo("Chrome");
        assertThat(device.extensionVersion()).isEqualTo("2.1.0");
        assertThat(device.online()).isTrue();

        // 閲覧イベントのバッチ
        BrowserExtensionModels.BatchSaveResult saved = browserExtensionService.saveEvents(issued.token(),
                batch("batch-1", List.of(
                        event("evt-1", "HISTORY_VISITED", "https://example.com/a", "2026-09-13T20:10:00+09:00"),
                        event("evt-2", "TAB_UPDATED", "https://example.com/b", "2026-09-13T20:11:00+09:00"),
                        event("evt-3", "NAV_COMMITTED", "https://example.com/c", "2026-09-13T20:12:00+09:00"))));
        assertThat(saved.receivedCount()).isEqualTo(3);
        assertThat(saved.insertedCount()).isEqualTo(3);
        assertThat(saved.duplicateCount()).isZero();

        // NET_Web閲覧履歴情報 に入っている
        WebBrowsingLogModels.BrowsingLogSearchResult found =
                webBrowsingLogService.search(DEVICE_ID, null, null, null, null, null, null, 1, 20);
        assertThat(found.totalElements()).isEqualTo(3);
        assertThat(found.items()).extracting(WebBrowsingLogModels.BrowsingLogRow::eventType)
                .containsExactlyInAnyOrder("HISTORY_VISITED", "TAB_UPDATED", "NAV_COMMITTED");
        assertThat(found.items()).allSatisfy(row -> {
            assertThat(row.terminalName()).isEqualTo("試験用PC");
            assertThat(row.domain()).isEqualTo("example.com");
            assertThat(row.activeFlag()).isEqualTo("1");
            assertThat(row.staySeconds()).isEqualTo(5);
        });

        // 送信の再試行（同じバッチをもう一度送る）→ 二重登録しない
        BrowserExtensionModels.BatchSaveResult resent = browserExtensionService.saveEvents(issued.token(),
                batch("batch-1", List.of(
                        event("evt-1", "HISTORY_VISITED", "https://example.com/a", "2026-09-13T20:10:00+09:00"),
                        event("evt-2", "TAB_UPDATED", "https://example.com/b", "2026-09-13T20:11:00+09:00"),
                        event("evt-3", "NAV_COMMITTED", "https://example.com/c", "2026-09-13T20:12:00+09:00"))));
        assertThat(resent.insertedCount()).isZero();
        assertThat(resent.duplicateCount()).isEqualTo(3);
        assertThat(webBrowsingLogService.search(DEVICE_ID, null, null, null, null, null, null, 1, 20)
                .totalElements()).isEqualTo(3);

        // 端末の最終送信日時が進む
        BrowserExtensionModels.DeviceRow after = browserExtensionService.registration(accountId)
                .devices().get(0);
        assertThat(after.lastSentAt()).isNotNull();
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> browserExtensionService.register("d".repeat(64),
                new BrowserExtensionModels.RegisterRequest(DEVICE_ID, null, null, null, null, null, null, null)))
                .isInstanceOf(UnauthenticatedException.class);

        assertThatThrownBy(() -> browserExtensionService.saveEvents("d".repeat(64),
                batch("batch-x", List.of(event("evt-x", "HISTORY_VISITED", "https://example.com/x",
                        "2026-09-13T20:10:00+09:00")))))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void oldTokenStopsWorkingAfterReissue() {
        long accountId = createStudentAccount();
        String oldToken = browserExtensionService.issueToken(accountId).token();
        String newToken = browserExtensionService.reissueToken(accountId).token();

        assertThat(newToken).isNotEqualTo(oldToken);
        assertThatThrownBy(() -> browserExtensionService.heartbeat(oldToken,
                new BrowserExtensionModels.HeartbeatRequest(DEVICE_ID, null)))
                .isInstanceOf(UnauthenticatedException.class);

        // 新しいコードでは通る
        browserExtensionService.heartbeat(newToken, new BrowserExtensionModels.HeartbeatRequest(DEVICE_ID, null));
        assertThat(browserExtensionService.registration(accountId).deviceCount()).isEqualTo(1);
    }

    // -------------------------------------------------------------------- 部品

    private BrowserExtensionModels.EventBatchRequest batch(String sentAt, List<BrowserExtensionModels.EventItem> events) {
        return new BrowserExtensionModels.EventBatchRequest(DEVICE_ID, "試験用PC", "Chrome", "131.0.0.0",
                "2.1.0", "Default", sentAt, events);
    }

    private BrowserExtensionModels.EventItem event(String key, String eventType, String url, String visitedAt) {
        return new BrowserExtensionModels.EventItem(key, eventType, url, "example.com", "試験ページ",
                "https://example.com/", "https://example.com/f.ico", "link", 3L, 1L, "session-1",
                true, false, visitedAt, 5, 1);
    }
}
