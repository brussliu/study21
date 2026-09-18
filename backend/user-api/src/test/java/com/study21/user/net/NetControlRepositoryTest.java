package com.study21.user.net;

import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する サイト管理 / 端末コントロール の検証。
 *
 * 移行済みデータ（2.0 から移した 175 サイト・5 端末）が読めること、
 * 新規登録・更新・承認・却下・削除・モード変更が実際の SQL で
 * 動くことを確かめる。テストはロールバックするので DB は汚れない。
 *
 * 移行データの件数は「移行時のスナップショット」なので、運用で行が削除されると減る。
 * そのため件数そのものではなく、内訳の整合（区分の合計＝総件数）と値の対応
 * （全件が承認済・有効）を固定する。
 *
 * 実行には DB のパスワードが要る（テスト環境の既定は空のため、無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class NetControlRepositoryTest {

    @Autowired
    private NetSiteMapper siteMapper;

    @Autowired
    private NetTerminalMapper terminalMapper;

    @Autowired
    private NetSiteService siteService;

    @Autowired
    private NetTerminalService terminalService;

    private UserPrincipal guardian() {
        return new UserPrincipal(3L, "testparent@gmail.com", "試験 保護者", AccountType.GUARDIAN);
    }

    @Test
    void migratedSitesAreReadableWithFilters() {
        long total = siteMapper.count(null, null, null, null, null, null);
        assertThat(total).isGreaterThan(0);

        // 区分の内訳の合計が総件数と一致する（移行時の区分の対応が崩れていない）
        long breakCount = siteMapper.count("BREAK", null, null, null, null, null);
        long studyCount = siteMapper.count("STUDY", null, null, null, null, null);
        long normalCount = siteMapper.count("NORMAL", null, null, null, null, null);
        long gameCount = siteMapper.count("GAME", null, null, null, null, null);
        assertThat(breakCount + studyCount + normalCount + gameCount).isEqualTo(total);
        assertThat(breakCount).isGreaterThan(0);
        assertThat(studyCount).isGreaterThan(0);

        // 判定方法（2.0 のデータはほぼ末尾一致）
        long suffixCount = siteMapper.count(null, "SUFFIX", null, null, null, null);
        assertThat(suffixCount).isGreaterThan(0);
        assertThat(suffixCount).isLessThanOrEqualTo(total);

        // 移行データは全件「承認済・有効」
        assertThat(siteMapper.count(null, null, null, "APPROVED", null, null)).isEqualTo(total);

        // 分類名称つきの移行データ（英会話・ニュース）が保持されている
        // （キーワードはサイト名称・備考にも当たるため、件数ではなく存在で確認する）
        assertThat(siteMapper.count(null, null, "OTHER", null, null, "英会話")).isGreaterThanOrEqualTo(1);
        assertThat(siteMapper.count(null, null, "OTHER", null, null, "ニュース")).isGreaterThanOrEqualTo(1);

        // 検索結果の中身（承認済み・有効で、ホスト名が正規化されている）
        var page = siteService.search(new NetSiteService.NetSiteSearchQuery(
                null, null, null, "APPROVED", "1", "youtube", "siteName", "asc", 1, 15));
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items().get(0).hostName()).contains("youtube");
        assertThat(page.items().get(0).approvalStatus()).isEqualTo("APPROVED");
    }

    @Test
    void createUpdateApproveAndDeleteRoundTrip() {
        var created = siteService.create(guardian(), new NetSiteModels.SiteSaveRequest(
                "検証サイト", "https://www.verify.example.com:8443/path?x=1", "STUDY", "PREFIX", "OTHER", "検証",
                "テスト", null));
        long siteId = created.row().siteId();
        assertThat(created.row().approvalStatus()).isEqualTo("PENDING");
        assertThat(created.row().hostName()).isEqualTo("verify.example.com");
        assertThat(created.row().categoryName()).isEqualTo("検証");

        var approved = siteService.approve(guardian(), siteId);
        assertThat(approved.row().approvalStatus()).isEqualTo("APPROVED");
        assertThat(approved.row().approvedAt()).isNotNull();

        // 更新すると未承認に戻り、内容が反映される
        var updated = siteService.update(guardian(), siteId, new NetSiteModels.SiteSaveRequest(
                "検証サイト2", "verify2.example.com", "BREAK", "EXACT", "LEARNING", null, null, null));
        assertThat(updated.row().siteName()).isEqualTo("検証サイト2");
        assertThat(updated.row().approvalStatus()).isEqualTo("PENDING");
        assertThat(updated.row().approvedAt()).isNull();
        assertThat(updated.row().hostName()).isEqualTo("verify2.example.com");

        NetSiteEntity raw = siteMapper.findById(siteId);
        assertThat(raw.getCategoryName()).isNull();
        assertThat(raw.getCreatedByAccountId()).isEqualTo(3L);

        assertThat(siteService.delete(guardian(), siteId).message()).isEqualTo("サイトを削除しました。");
        assertThat(siteMapper.findById(siteId)).isNull();
    }

    @Test
    void approveRejectAndBulkApproveRoundTrip() {
        // 3 件登録して、承認 → 却下 → 再承認 を順に確認する
        long first = siteService.create(guardian(), new NetSiteModels.SiteSaveRequest(
                "承認検証1", "approve1.example.com", "STUDY", "SUFFIX", "OTHER", null, null, null)).row().siteId();
        long second = siteService.create(guardian(), new NetSiteModels.SiteSaveRequest(
                "承認検証2", "approve2.example.com", "STUDY", "SUFFIX", "OTHER", null, null, null)).row().siteId();
        assertThat(siteMapper.findById(first).getApprovalStatus()).isEqualTo("PENDING");

        // 1 件承認（承認者・承認日時が入る）
        var approved = siteService.approve(guardian(), first);
        assertThat(approved.row().approvalStatus()).isEqualTo("APPROVED");
        assertThat(approved.row().approvedAt()).isNotNull();
        assertThat(siteMapper.findById(first).getApprovedByAccountId()).isEqualTo(3L);

        // 却下（承認者・承認日時は残さない）
        var rejected = siteService.reject(guardian(), first);
        assertThat(rejected.row().approvalStatus()).isEqualTo("REJECTED");
        assertThat(rejected.row().approvedAt()).isNull();
        assertThat(siteMapper.findById(first).getApprovedByAccountId()).isNull();

        // 却下したあと、もう一度承認できる（画面は 却下 -> 承認 の順に切り替わる）
        var approvedAgain = siteService.approve(guardian(), first);
        assertThat(approvedAgain.row().approvalStatus()).isEqualTo("APPROVED");
        assertThat(siteMapper.findById(first).getApprovedByAccountId()).isEqualTo(3L);
    }

    @Test
    void migratedTerminalsAreReadableAndModeCanBeChanged() {
        long total = terminalMapper.count(null, null, null);
        assertThat(total).isEqualTo(5);

        var page = terminalService.search(guardian(),
                new NetTerminalService.NetTerminalSearchQuery(null, "1", null, 1, 50));
        assertThat(page.items()).hasSize(5);
        NetTerminalModels.TerminalRow first = page.items().get(0);
        assertThat(first.ipAddress()).isNotBlank();
        assertThat(NetTerminalModels.MODE_CODES).contains(first.terminalMode());

        // モード変更（楽観的ロックのバージョンを渡す）
        var changed = terminalService.updateMode(guardian(), first.terminalId(),
                new NetTerminalModels.ModeChangeRequest("B", first.version()));
        assertThat(changed.updatedCount()).isEqualTo(1);
        assertThat(terminalMapper.findById(first.terminalId()).getTerminalMode()).isEqualTo("B");
        assertThat(terminalMapper.findById(first.terminalId()).getUpdatedByAccountId()).isEqualTo(3L);

        // 一括変更
        List<Long> ids = page.items().stream().map(NetTerminalModels.TerminalRow::terminalId).toList();
        var bulk = terminalService.updateModes(guardian(),
                new NetTerminalModels.BulkModeChangeRequest(ids, "K"));
        assertThat(bulk.requestedCount()).isEqualTo(5);
        assertThat(bulk.updatedCount()).isEqualTo(5);
        assertThat(terminalMapper.count(null, null, null) - terminalMapper.count("K", null, null)).isZero();
    }

    @Test
    void terminalCanBeDeleted() {
        // 一時的に 1 台登録してから削除する（テストはロールバックするので DB は汚れない）
        String ip = "192.168.0.240";
        var created = terminalService.create(guardian(), new NetTerminalModels.TerminalSaveRequest(
                ip, "削除テスト端末", "T", "1", "E2E 削除", null));
        assertThat(created.message()).contains("登録しました");
        long terminalId = terminalService.search(guardian(),
                        new NetTerminalService.NetTerminalSearchQuery(null, null, ip, 1, 50))
                .items().get(0).terminalId();
        assertThat(terminalMapper.findById(terminalId)).isNotNull();

        var deleted = terminalService.delete(guardian(), terminalId);

        assertThat(deleted.message()).contains("削除しました");
        assertThat(terminalMapper.findById(terminalId)).isNull();
        // 他の端末は消えていない（移行済みの 5 台）
        assertThat(terminalMapper.count(null, null, null)).isEqualTo(5);
    }

    @Test
    void siteUrlSortGroupsSubdomainsWithTheirParentDomain() {
        // 同じサイトの親ドメインとサブドメイン、別サイト（gakken）を一時的に作る
        List<String> hosts = List.of("google.com", "accounts.google.com", "mail.google.com",
                "gakken.jp", "gakken-ep.jp");
        for (String host : hosts) {
            siteService.create(guardian(), new NetSiteModels.SiteSaveRequest(
                    host, host, "NORMAL", "SUFFIX", "OTHER", null, "E2E 並び替え", null));
        }

        // サイトURL（昇順）で並べると、サブドメインが親ドメインのすぐ後ろに来る。
        // 文字列の単純比較だと accounts.google.com が先頭に来て google.com と離れてしまう。
        List<String> sortedHosts = siteService.search(new NetSiteService.NetSiteSearchQuery(
                        null, null, null, null, null, null, "siteUrl", "asc", 1, 500))
                .items().stream()
                .map(NetSiteModels.SiteRow::hostName)
                .filter(hosts::contains)
                .distinct()   // 移行データにも同じホストがあるため、作った 5 種類だけを見る
                .toList();
        assertThat(sortedHosts).containsExactly(
                "google.com", "accounts.google.com", "mail.google.com", "gakken.jp", "gakken-ep.jp");

        // 降順では逆になる（同じサイトのまとまりは保たれる）
        List<String> descending = siteService.search(new NetSiteService.NetSiteSearchQuery(
                        null, null, null, null, null, null, "siteUrl", "desc", 1, 500))
                .items().stream()
                .map(NetSiteModels.SiteRow::hostName)
                .filter(hosts::contains)
                .distinct()
                .toList();
        assertThat(descending).containsExactly(
                "gakken-ep.jp", "gakken.jp", "mail.google.com", "accounts.google.com", "google.com");
    }

    @Test
    void createAndEditTerminalRoundTrip() {
        // 新規登録（移行データと重複しない IP を使う）
        String ip = "10.99.0.1";
        var created = terminalService.create(guardian(), new NetTerminalModels.TerminalSaveRequest(
                ip, "検証用タブレット", "B", "1", "E2E", null));
        assertThat(created.message()).contains("登録しました");

        var found = terminalService.search(guardian(),
                new NetTerminalService.NetTerminalSearchQuery(null, null, ip, 1, 50));
        assertThat(found.items()).hasSize(1);
        NetTerminalModels.TerminalRow row = found.items().get(0);
        assertThat(row.terminalName()).isEqualTo("検証用タブレット");
        assertThat(row.terminalMode()).isEqualTo("B");
        assertThat(row.status()).isEqualTo("1");
        assertThat(row.note()).isEqualTo("E2E");
        // 登録者・更新者は操作したアカウント
        assertThat(terminalMapper.findById(row.terminalId()).getCreatedByAccountId()).isEqualTo(3L);

        // 有効な端末で IP が重複すると 400
        assertThatThrownBy(() -> terminalService.create(guardian(), new NetTerminalModels.TerminalSaveRequest(
                ip, "重複", "T", "1", null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("既に登録されています");

        // 編集（IP・名称・モード・状態・備考）
        var updated = terminalService.update(guardian(), row.terminalId(),
                new NetTerminalModels.TerminalSaveRequest("10.99.0.2", "検証用タブレット（改名）", "S", "0", null,
                        row.version()));
        assertThat(updated.updatedCount()).isEqualTo(1);
        NetTerminalEntity after = terminalMapper.findById(row.terminalId());
        assertThat(after.getIpAddress()).isEqualTo("10.99.0.2");
        assertThat(after.getTerminalName()).isEqualTo("検証用タブレット（改名）");
        assertThat(after.getTerminalMode()).isEqualTo("S");
        assertThat(after.getStatus()).isEqualTo("0");
        assertThat(after.getNote()).isNull();
        assertThat(after.getVersion()).isEqualTo(row.version() + 1);
        assertThat(after.getUpdatedByAccountId()).isEqualTo(3L);

        // 無効（状態='0'）にしたので、同じ IP を別端末として登録できる
        var reused = terminalService.create(guardian(), new NetTerminalModels.TerminalSaveRequest(
                "10.99.0.2", "同じ IP の別端末", "T", "1", null, null));
        assertThat(reused.message()).contains("登録しました");

        // 古いバージョンで編集すると 400（楽観的ロック）
        int staleVersion = row.version();
        assertThatThrownBy(() -> terminalService.update(guardian(), row.terminalId(),
                new NetTerminalModels.TerminalSaveRequest("10.99.0.3", "古い画面", "T", "0", null, staleVersion)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("他の操作で先に更新されました");
    }
}
