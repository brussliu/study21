package com.study21.user.net;

import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 端末コントロールの実装。2.0 の TerminalControlServiceImpl の検証・メッセージを引き継ぐ。
 *
 * <p>2.0 は保護者だけが使えたが、2.1 は**当面ロールで分けない**（ログインしていれば
 * 生徒・保護者とも使える。ユーザーの指定。ロールの分離は後で設計する）。
 * ここで見るのは「ログインしているか」だけ。</p>
 */
@Service
public class NetTerminalServiceImpl implements NetTerminalService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    /** IP に使える文字（IPv4 / IPv6）。DB の CHECK と同じ範囲。 */
    private static final Pattern IP_CHARS = Pattern.compile("^[0-9A-Fa-f:.]{1,45}$");
    /** IPv4 の 1 オクテット。 */
    private static final Pattern IPV4_PART = Pattern.compile("^\\d{1,3}$");
    /** IPv6 の 1 グループ（1〜4 桁の 16 進）。 */
    private static final Pattern IPV6_GROUP = Pattern.compile("^[0-9A-Fa-f]{1,4}$");

    private final NetTerminalMapper terminalMapper;

    public NetTerminalServiceImpl(NetTerminalMapper terminalMapper) {
        this.terminalMapper = terminalMapper;
    }

    @Override
    public NetTerminalModels.TerminalSearchResult search(UserPrincipal user, NetTerminalSearchQuery query) {
        requireLogin(user);
        NetTerminalSearchQuery condition = query == null
                ? new NetTerminalSearchQuery(null, null, null, null, null)
                : query;

        String mode = normalizeMode(condition.terminalMode(), false);
        String status = normalizeStatus(condition.status());
        String keyword = condition.keyword() == null || condition.keyword().isBlank()
                ? null
                : condition.keyword().trim();

        int size = Math.max(1, Math.min(condition.size() == null ? DEFAULT_PAGE_SIZE : condition.size(), MAX_PAGE_SIZE));
        long total = terminalMapper.count(mode, status, keyword);
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(condition.page() == null ? 1 : condition.page(), 1), totalPages);

        List<NetTerminalModels.TerminalRow> items = terminalMapper
                .search(mode, status, keyword, size, (page - 1) * size)
                .stream()
                .map(NetTerminalModels.TerminalRow::of)
                .toList();

        return new NetTerminalModels.TerminalSearchResult(items, page, size, total, totalPages);
    }

    @Override
    @Transactional
    public NetTerminalModels.TerminalMutationResult create(UserPrincipal user,
                                                           NetTerminalModels.TerminalSaveRequest request) {
        requireLogin(user);
        String ipAddress = normalizeIpAddress(request == null ? null : request.ipAddress());
        String terminalName = normalizeTerminalName(request == null ? null : request.terminalName());
        String mode = normalizeMode(request == null ? null : request.terminalMode(), true);
        String status = normalizeRequiredStatus(request == null ? null : request.status());
        String note = normalizeNote(request == null ? null : request.note());
        requireIpAvailable(ipAddress, null);

        NetTerminalEntity entity = new NetTerminalEntity();
        entity.setIpAddress(ipAddress);
        entity.setTerminalName(terminalName);
        entity.setTerminalMode(mode);
        entity.setStatus(status);
        entity.setNote(note);
        entity.setCreatedByAccountId(user.accountId());
        entity.setUpdatedByAccountId(user.accountId());
        terminalMapper.insert(entity);

        return new NetTerminalModels.TerminalMutationResult("端末を登録しました。", 1, 1);
    }

    @Override
    @Transactional
    public NetTerminalModels.TerminalMutationResult update(UserPrincipal user, long terminalId,
                                                          NetTerminalModels.TerminalSaveRequest request) {
        requireLogin(user);
        if (terminalId <= 0) {
            throw new ValidationException("端末IDが不正です。");
        }
        if (terminalMapper.findById(terminalId) == null) {
            throw new NotFoundException("更新対象の端末が見つかりません。");
        }
        String ipAddress = normalizeIpAddress(request == null ? null : request.ipAddress());
        String terminalName = normalizeTerminalName(request == null ? null : request.terminalName());
        String mode = normalizeMode(request == null ? null : request.terminalMode(), true);
        String status = normalizeRequiredStatus(request == null ? null : request.status());
        String note = normalizeNote(request == null ? null : request.note());
        requireIpAvailable(ipAddress, terminalId);

        int updated = terminalMapper.update(terminalId, ipAddress, terminalName, mode, status, note,
                request == null ? null : request.version(), user.accountId());
        if (updated == 0) {
            throw new ValidationException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new NetTerminalModels.TerminalMutationResult("端末を更新しました。", 1, updated);
    }

    @Override
    @Transactional
    public NetTerminalModels.TerminalMutationResult delete(UserPrincipal user, long terminalId) {
        requireLogin(user);
        if (terminalId <= 0) {
            throw new ValidationException("端末を指定してください。");
        }
        if (terminalMapper.delete(terminalId) == 0) {
            throw new NotFoundException("対象端末が存在しません。");
        }
        // 2.0 はモード変更で batL01（プロキシ再起動）を起動していたが、2.1 は batS01 が
        // admin-api の起動時に立ち上げるため、削除でも再起動しない（DB 更新のみ）。
        return new NetTerminalModels.TerminalMutationResult("端末を削除しました。", 1, 1);
    }

    @Override
    @Transactional
    public NetTerminalModels.TerminalMutationResult updateMode(UserPrincipal user, long terminalId,
                                                              NetTerminalModels.ModeChangeRequest request) {
        requireLogin(user);
        if (terminalId <= 0) {
            throw new ValidationException("端末IDが不正です。");
        }
        String mode = normalizeMode(request == null ? null : request.terminalMode(), true);
        if (terminalMapper.findById(terminalId) == null) {
            throw new NotFoundException("更新対象の端末が見つかりません。");
        }
        int updated = terminalMapper.updateMode(terminalId, mode,
                request == null ? null : request.version(), user.accountId());
        if (updated == 0) {
            throw new ValidationException("他の保護者が先に更新しました。再読み込みしてください。");
        }
        // 2.0 はここで batL01（プロキシ再起動）を起動していた。2.1 のプロキシは batS01 が admin-api の起動時に立ち上げるため、ここでは DB 更新のみ。
        return new NetTerminalModels.TerminalMutationResult("端末ステータスを更新しました。", 1, updated);
    }

    @Override
    @Transactional
    public NetTerminalModels.TerminalMutationResult updateModes(UserPrincipal user,
                                                                NetTerminalModels.BulkModeChangeRequest request) {
        requireLogin(user);
        List<Long> terminalIds = request == null || request.terminalIds() == null
                ? List.of()
                : request.terminalIds().stream().filter(id -> id != null && id > 0).distinct().toList();
        if (terminalIds.isEmpty()) {
            throw new ValidationException("更新対象の端末を選択してください。");
        }
        String mode = normalizeMode(request.terminalMode(), true);

        int updated = terminalMapper.updateModes(terminalIds, mode, user.accountId());
        // 2.0 はここで batL01（プロキシ再起動）を起動していた。2.1 のプロキシは batS01 が admin-api の起動時に立ち上げるため、ここでは DB 更新のみ。
        return new NetTerminalModels.TerminalMutationResult(
                "選択した端末のステータスを更新しました。", terminalIds.size(), updated);
    }

    /**
     * IP アドレスの書式を確かめる（IPv4 / IPv6）。
     * DB の CHECK は「使える文字」だけを見るので、桁や区切りの誤りはここで弾く。
     */
    private static String normalizeIpAddress(String value) {
        String ip = value == null ? "" : value.trim();
        if (ip.isEmpty()) {
            throw new ValidationException("IPアドレスを入力してください。");
        }
        if (!IP_CHARS.matcher(ip).matches()) {
            throw new ValidationException("IPアドレスの書式が正しくありません（例: 192.168.0.10）。");
        }
        boolean valid = ip.contains(":") ? isIpv6(ip) : isIpv4(ip);
        if (!valid) {
            throw new ValidationException("IPアドレスの書式が正しくありません（例: 192.168.0.10）。");
        }
        return ip;
    }

    private static boolean isIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (!IPV4_PART.matcher(part).matches()) {
                return false;
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    /** IPv6（`::` による省略を含む）。厳密な全形式ではなく、明らかな誤りを弾く。 */
    private static boolean isIpv6(String ip) {
        if (ip.contains(":::")) {
            return false;
        }
        String[] halves = ip.split("::", -1);
        if (halves.length > 2) {
            return false;
        }
        int groups = 0;
        for (int index = 0; index < halves.length; index += 1) {
            String half = halves[index];
            if (half.isEmpty()) {
                continue;
            }
            String[] parts = half.split(":", -1);
            for (String part : parts) {
                if (!IPV6_GROUP.matcher(part).matches()) {
                    return false;
                }
                groups += 1;
            }
        }
        // `::` があれば 8 グループ未満でよい（省略されている）
        return halves.length == 2 ? groups < 8 : groups == 8;
    }

    private static String normalizeTerminalName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new ValidationException("端末名称を入力してください。");
        }
        if (name.length() > 100) {
            throw new ValidationException("端末名称は100文字以内で入力してください。");
        }
        return name;
    }

    private static String normalizeRequiredStatus(String value) {
        String status = normalizeStatus(value);
        if (status == null) {
            throw new ValidationException("状態が不正です。");
        }
        return status;
    }

    private static String normalizeNote(String value) {
        String note = value == null ? "" : value.trim();
        if (note.isEmpty()) {
            return null;
        }
        if (note.length() > 200) {
            throw new ValidationException("備考は200文字以内で入力してください。");
        }
        return note;
    }

    /** 有効（状態='1'）な端末で IP が重複していないか確かめる。 */
    private void requireIpAvailable(String ipAddress, Long excludeTerminalId) {
        if (terminalMapper.findActiveByIpAddress(ipAddress, excludeTerminalId) != null) {
            throw new ValidationException("このIPアドレスは既に登録されています（有効な端末）。");
        }
    }

    /**
     * ログインしていることだけを確かめる。
     * 2.0 はここで保護者に限定していたが、2.1 は当面ロールで分けない（API は認証必須）。
     */
    private void requireLogin(UserPrincipal user) {
        if (user == null) {
            throw new ValidationException("ログインが必要です。");
        }
    }

    private static String normalizeMode(String value, boolean required) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            if (required) {
                throw new ValidationException("端末ステータスが不正です。");
            }
            return null;
        }
        if (!NetTerminalModels.MODE_CODES.contains(normalized)) {
            throw new ValidationException("端末ステータスが不正です。");
        }
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!NetTerminalModels.STATUSES.contains(normalized)) {
            throw new ValidationException("状態が不正です。");
        }
        return normalized;
    }
}
