package com.study21.user.net;

import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * サイト管理の実装。2.0 の SiteServiceImpl の検証・メッセージ・ページング既定値を引き継ぐ。
 */
@Service
public class NetSiteServiceImpl implements NetSiteService {

    /** 2.0 と同じ既定値（1 ページ 15 件・最大 200 件）。 */
    private static final int DEFAULT_PAGE_SIZE = 15;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE = 1;

    /** 画面の列名 → DB の列名（並び替えの許可リスト。SQL へはこの値だけを渡す）。 */
    private static final Map<String, String> SORTABLE_COLUMNS = sortableColumns();

    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://");

    private final NetSiteMapper siteMapper;

    public NetSiteServiceImpl(NetSiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }

    /**
     * サイトURL の並び替えキー。
     *
     * <p>ホスト名のラベルを**逆順**にして比較する（`accounts.google.com` → `com.google.accounts`）。
     * こうすると同じサイトのサブドメインが親のすぐ後ろに並ぶ（ユーザーの指定）:
     * `google.com` → `accounts.google.com` → `gakken.jp` → `gakken-ep.jp`。</p>
     *
     * <p>文字列の単純比較だと `accounts.google.com` と `google.com` が離れてしまう。
     * ホスト名が空の行はサイトURL で代用する。</p>
     */
    private static final String SITE_URL_SORT_KEY =
            "COALESCE((SELECT string_agg(label.part, '.' ORDER BY label.pos DESC)"
                    + "   FROM unnest(string_to_array(LOWER(COALESCE(NULLIF(\"ホスト名\", ''), \"サイトURL\")), '.'))"
                    + "        WITH ORDINALITY AS label(part, pos)), '')";

    private static Map<String, String> sortableColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("sitename", "\"サイト名称\"");
        columns.put("siteurl", SITE_URL_SORT_KEY);
        columns.put("kind", "\"区分コード\"");
        columns.put("judgemethod", "\"判定方法コード\"");
        columns.put("category", "\"分類コード\"");
        columns.put("approvalstatus", "\"承認ステータス\"");
        columns.put("status", "\"状態\"");
        columns.put("memo", "\"備考\"");
        columns.put("updatedat", "\"更新日時\"");
        columns.put("createdat", "\"登録日時\"");
        return Map.copyOf(columns);
    }

    @Override
    public NetSiteModels.SiteSearchResult search(NetSiteSearchQuery query) {
        NetSiteSearchQuery condition = query == null
                ? new NetSiteSearchQuery(null, null, null, null, null, null, null, null, null, null)
                : query;

        String kindCode = code(condition.kindCode(), NetSiteModels.KIND_CODES, "区分");
        String judgeMethodCode = code(condition.judgeMethodCode(), NetSiteModels.JUDGE_METHOD_CODES, "判定方法");
        String categoryCode = code(condition.categoryCode(), NetSiteModels.CATEGORY_CODES, "分類");
        String approvalStatus = code(condition.approvalStatus(), NetSiteModels.APPROVAL_STATUSES, "承認ステータス");
        String status = code(condition.status(), NetSiteModels.STATUSES, "ステータス");
        String keyword = blankToNull(condition.keyword());

        int size = clampSize(condition.size());
        long total = siteMapper.count(kindCode, judgeMethodCode, categoryCode, approvalStatus, status, keyword);
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(condition.page() == null ? DEFAULT_PAGE : condition.page(), 1), totalPages);
        int offset = (page - 1) * size;

        List<NetSiteModels.SiteRow> items = siteMapper
                .search(kindCode, judgeMethodCode, categoryCode, approvalStatus, status, keyword,
                        sortColumn(condition.sortBy()), sortDirection(condition.sortDir()), size, offset)
                .stream()
                .map(NetSiteModels.SiteRow::of)
                .toList();

        return new NetSiteModels.SiteSearchResult(items, page, size, total, totalPages);
    }

    @Override
    @Transactional
    public NetSiteModels.SiteMutationResult create(UserPrincipal user, NetSiteModels.SiteSaveRequest request) {
        NetSiteEntity entity = new NetSiteEntity();
        applyRequest(entity, request);
        // 2.0 と同じく新規は未承認で登録する
        entity.setApprovalStatus("PENDING");
        entity.setStatus("1");
        entity.setCreatedByAccountId(user.accountId());
        entity.setUpdatedByAccountId(user.accountId());

        siteMapper.insert(entity);
        // 2.0 はここで batL01（プロキシ再起動）を起動していた。2.1 のプロキシは batS01 が admin-api の起動時に立ち上げるため、ここでは DB 更新のみ。
        return NetSiteModels.SiteMutationResult.of("サイトを登録しました。", siteMapper.findById(entity.getSiteId()));
    }

    @Override
    @Transactional
    public NetSiteModels.SiteMutationResult update(UserPrincipal user, long siteId,
                                                  NetSiteModels.SiteSaveRequest request) {
        NetSiteEntity current = siteMapper.findById(siteId);
        if (current == null) {
            throw new NotFoundException("対象サイトが存在しません。");
        }
        NetSiteEntity entity = new NetSiteEntity();
        entity.setSiteId(siteId);
        applyRequest(entity, request);
        entity.setStatus(request == null ? "1" : (current.getStatus() == null ? "1" : current.getStatus()));
        // 画面が見ていたバージョンで照合する（未指定なら現在値）
        entity.setVersion(request == null || request.version() == null ? current.getVersion() : request.version());
        entity.setUpdatedByAccountId(user.accountId());

        if (siteMapper.update(entity) == 0) {
            // バージョン不一致（他の管理者が先に更新した）
            throw new ValidationException("他の管理者が先に更新しました。再読み込みしてください。");
        }
        // 2.0 はここで batL01（プロキシ再起動）を起動していた。2.1 のプロキシは batS01 が admin-api の起動時に立ち上げるため、ここでは DB 更新のみ。
        return NetSiteModels.SiteMutationResult.of("サイトを更新しました。", siteMapper.findById(siteId));
    }

    @Override
    @Transactional
    public NetSiteModels.SiteMutationResult delete(UserPrincipal user, long siteId) {
        if (siteMapper.delete(siteId) == 0) {
            throw new NotFoundException("対象サイトが存在しません。");
        }
        // 2.0 はここで batL01（プロキシ再起動）を起動していた。2.1 のプロキシは batS01 が admin-api の起動時に立ち上げるため、ここでは DB 更新のみ。
        return NetSiteModels.SiteMutationResult.of("サイトを削除しました。", null);
    }

    @Override
    @Transactional
    public NetSiteModels.SiteMutationResult approve(UserPrincipal user, long siteId) {
        NetSiteEntity current = siteMapper.findById(siteId);
        if (current == null) {
            throw new NotFoundException("対象サイトが存在しません。");
        }
        siteMapper.approve(siteId, user.accountId(), new Timestamp(System.currentTimeMillis()), user.accountId());
        // 2.0 はここで batL01（プロキシ再起動）を起動していた。2.1 のプロキシは batS01 が admin-api の起動時に立ち上げるため、ここでは DB 更新のみ。
        return NetSiteModels.SiteMutationResult.of("サイトを承認しました。", siteMapper.findById(siteId));
    }

    @Override
    @Transactional
    public NetSiteModels.SiteMutationResult reject(UserPrincipal user, long siteId) {
        NetSiteEntity current = siteMapper.findById(siteId);
        if (current == null) {
            throw new NotFoundException("対象サイトが存在しません。");
        }
        siteMapper.reject(siteId, user.accountId());
        // 2.0 はここで batL01（プロキシ再起動）を起動していた。2.1 のプロキシは batS01 が admin-api の起動時に立ち上げるため、ここでは DB 更新のみ。
        return NetSiteModels.SiteMutationResult.of("サイトを却下しました。", siteMapper.findById(siteId));
    }

    /** リクエスト内容を検証してエンティティへ移す（分類名称は OTHER のときだけ保持する）。 */
    private void applyRequest(NetSiteEntity entity, NetSiteModels.SiteSaveRequest request) {
        if (request == null) {
            throw new ValidationException("サイト名称を入力してください。");
        }
        String siteName = trim(request.siteName());
        String siteUrl = trim(request.siteUrl());
        if (siteName.isEmpty()) {
            throw new ValidationException("サイト名称を入力してください。");
        }
        if (siteUrl.isEmpty()) {
            throw new ValidationException("サイトURLを入力してください。");
        }
        String hostName = normalizeHost(siteUrl);
        if (hostName.isEmpty()) {
            throw new ValidationException("サイトURLからホスト名を取得できません。");
        }

        String kindCode = code(request.kindCode(), NetSiteModels.KIND_CODES, "区分");
        if (kindCode == null) {
            throw new ValidationException("区分を選択してください。");
        }
        String categoryCode = code(request.categoryCode(), NetSiteModels.CATEGORY_CODES, "分類");
        if (categoryCode == null) {
            throw new ValidationException("分類を選択してください。");
        }
        String judgeMethodCode = code(request.judgeMethodCode(), NetSiteModels.JUDGE_METHOD_CODES, "判定方法");
        if (judgeMethodCode == null) {
            judgeMethodCode = "SUFFIX"; // 2.0 の既定は末尾一致
        }

        entity.setSiteName(siteName);
        entity.setSiteUrl(siteUrl);
        entity.setHostName(hostName);
        entity.setKindCode(kindCode);
        entity.setJudgeMethodCode(judgeMethodCode);
        entity.setCategoryCode(categoryCode);
        entity.setCategoryName("OTHER".equals(categoryCode) ? blankToNull(request.categoryName()) : null);
        entity.setNote(blankToNull(request.note()));
    }

    /**
     * URL から判定用のホスト名を作る（移行スクリプトと同じ規則）。
     * 小文字化 → スキーム除去 → ポート/パス/クエリ除去 → 先頭の www. 除去。
     */
    static String normalizeHost(String url) {
        String value = trim(url).toLowerCase(Locale.ROOT);
        value = SCHEME.matcher(value).replaceFirst("");
        int cut = value.length();
        for (char separator : new char[] {'/', ':', '?'}) {
            int index = value.indexOf(separator);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        value = value.substring(0, cut);
        while (value.startsWith("www.")) {
            value = value.substring(4);
        }
        return value;
    }

    /** 許可リストに無いコードはエラーにする（null / 空は「指定なし」として null を返す）。 */
    private static String code(String value, List<String> allowed, String label) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw new ValidationException(label + "が不正です。");
        }
        return upper;
    }

    private static int clampSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private static String sortColumn(String sortBy) {
        String key = blankToNull(sortBy);
        if (key == null) {
            return SORTABLE_COLUMNS.get("createdat");
        }
        String column = SORTABLE_COLUMNS.get(key.toLowerCase(Locale.ROOT));
        if (column == null) {
            throw new ValidationException("並び替えの指定が不正です。");
        }
        return column;
    }

    private static String sortDirection(String sortDir) {
        return "asc".equalsIgnoreCase(blankToNull(sortDir)) ? "ASC" : "DESC";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
