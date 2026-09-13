package com.study21.user.linkclip;

import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * リンククリップはアカウント個人所有（親子共有ではない）。
 * すべての検索・更新・削除で所有者アカウントIDを条件に含める。
 */
@Service
public class LinkClipService {
    private static final Set<String> FOLDERS = Set.of("INBOX", "READ_LATER", "LEARNING", "REFERENCE", "DONE");
    private static final Set<String> SOURCES =
            Set.of("WEB", "YOUTUBE", "GITHUB", "WIKIPEDIA", "NEWS", "LOCAL_FILE", "OTHER");
    private static final Set<String> CLIP_TYPES =
            Set.of("LINK", "VIDEO", "ARTICLE", "REPOSITORY", "NEWS", "FILE");
    private static final int MAX_TAG_LENGTH = 100;

    private final LinkClipMapper mapper;
    private final LinkClipMetadataFetcher metadataFetcher;
    private final AccountMapper accountMapper;

    public LinkClipService(LinkClipMapper mapper, LinkClipMetadataFetcher metadataFetcher,
                           AccountMapper accountMapper) {
        this.mapper = mapper;
        this.metadataFetcher = metadataFetcher;
        this.accountMapper = accountMapper;
    }

    // ---------- 一覧 ----------

    @Transactional(readOnly = true)
    public LinkClipModels.Workspace workspace(UserPrincipal user, String folderCode, String sourceCode,
                                              String clipType, String tag, String keyword,
                                              boolean favoriteOnly, String archiveState) {
        long ownerId = user.accountId();
        String folder = normalizeFilter(folderCode, FOLDERS);
        String source = normalizeFilter(sourceCode, SOURCES);
        String type = normalizeFilter(clipType, CLIP_TYPES);
        String state = archiveState == null || archiveState.isBlank() ? "active" : archiveState.trim();
        if (!List.of("active", "archived", "all").contains(state)) state = "active";

        List<LinkClipEntity> clips = mapper.search(ownerId, folder, source, type, trimToNull(tag),
                trimToNull(keyword), favoriteOnly, state);
        attachTags(ownerId, clips);
        return new LinkClipModels.Workspace(clips.stream().map(this::toRow).toList(),
                mapper.findTagOptions(ownerId),
                mapper.countByFolder(ownerId, source, type, trimToNull(tag), trimToNull(keyword), favoriteOnly, state));
    }

    // ---------- プレビュー（DB 書き込みなし） ----------

    @Transactional(readOnly = true)
    public LinkClipModels.Preview preview(UserPrincipal user, String url) {
        String raw = trimToNull(url);
        if (raw == null) throw LinkClipApiException.invalid("URLを入力してください。");
        LinkClipMetadataFetcher.Metadata metadata = metadataFetcher.fetch(raw);
        String normalized = LinkClipUrls.normalize(raw);
        String sourceCode = LinkClipUrls.detectSourceCode(raw);
        String clipType = LinkClipUrls.defaultClipType(sourceCode);
        String siteName = firstNonNull(metadata.siteName(), LinkClipUrls.hostOf(raw), "ローカルファイル");
        return new LinkClipModels.Preview(raw, normalized, sourceCode, clipType, siteName,
                resolveTitle(null, metadata, raw, siteName), metadata.description(), metadata.publisherName(),
                metadata.publishedAt(), metadata.videoSeconds(), metadata.thumbnailUrl(),
                LinkClipUrls.isLocalPath(raw));
    }

    // ---------- 登録・更新・削除 ----------

    @Transactional
    public LinkClipModels.Saved create(UserPrincipal user, LinkClipModels.SaveRequest request) {
        long ownerId = user.accountId();
        // 「お子さまにも登録」の検証は登録前に行う（片方だけ残る状態を作らない）
        Long studentOwnerId = resolveStudentOwnerId(user, request);

        LinkClipEntity clip = buildClip(ownerId, request.url(), request.pageTitle(), request.siteName(),
                request.folderCode(), request.sourceCode(), request.clipType(), request.summary(),
                request.aiSummary(), request.memo(), request.publisherName(), request.publishedAt(),
                request.videoSeconds(), request.thumbnailUrl(), request.favorite(), request.archived());
        mapper.insert(clip);
        replaceTags(ownerId, clip.getLinkClipId(), request.tags());

        if (studentOwnerId != null) {
            LinkClipEntity copy = copyFor(clip, studentOwnerId);
            mapper.insert(copy);
            replaceTags(studentOwnerId, copy.getLinkClipId(), request.tags());
        }
        return new LinkClipModels.Saved(rowOf(ownerId, clip.getLinkClipId()));
    }

    /**
     * 保護者が「お子さまのリンククリップにも登録する」を選んだときの登録先アカウント。
     * 選んでいなければ null。保護者以外が指定した場合や、お子さまが紐づいていない場合は 400 にする
     * （登録先はクライアントから受け取らず、保護者ID から必ず引き直す）。
     */
    private Long resolveStudentOwnerId(UserPrincipal user, LinkClipModels.SaveRequest request) {
        if (!Boolean.TRUE.equals(request.alsoForStudent())) {
            return null;
        }
        if (user.accountType() != AccountType.GUARDIAN) {
            throw LinkClipApiException.invalid("この操作は保護者アカウントでのみ利用できます。");
        }
        AccountEntity student = accountMapper.findStudentByGuardianId(user.accountId());
        if (student == null) {
            throw LinkClipApiException.invalid("お子さまのアカウントが見つからないため、お子さまには登録できません。");
        }
        return student.getAccountId();
    }

    /** 同じ内容を別の所有者向けに複製する（ID・版・閲覧状況・取得日時は引き継がない）。 */
    private LinkClipEntity copyFor(LinkClipEntity source, long ownerId) {
        LinkClipEntity copy = new LinkClipEntity();
        copy.setOwnerAccountId(ownerId);
        copy.setFolderCode(source.getFolderCode());
        copy.setSourceCode(source.getSourceCode());
        copy.setClipType(source.getClipType());
        copy.setSiteName(source.getSiteName());
        copy.setPageTitle(source.getPageTitle());
        copy.setUrl(source.getUrl());
        copy.setNormalizedUrl(source.getNormalizedUrl());
        copy.setSummary(source.getSummary());
        copy.setAiSummary(source.getAiSummary());
        copy.setMemo(source.getMemo());
        copy.setPublisherName(source.getPublisherName());
        copy.setPublishedAt(source.getPublishedAt());
        copy.setVideoSeconds(source.getVideoSeconds());
        copy.setThumbnailUrl(source.getThumbnailUrl());
        copy.setFavorite(source.isFavorite());
        copy.setArchived(source.isArchived());
        return copy;
    }

    @Transactional
    public LinkClipModels.Saved update(UserPrincipal user, long linkClipId, LinkClipModels.UpdateRequest request) {
        long ownerId = user.accountId();
        requireClip(ownerId, linkClipId);
        if (request.version() == null) throw LinkClipApiException.invalid("バージョンが指定されていません。");
        LinkClipEntity clip = buildClip(ownerId, request.url(), request.pageTitle(), request.siteName(),
                request.folderCode(), request.sourceCode(), request.clipType(), request.summary(),
                request.aiSummary(), request.memo(), request.publisherName(), request.publishedAt(),
                request.videoSeconds(), request.thumbnailUrl(), request.favorite(), request.archived());
        clip.setLinkClipId(linkClipId);
        int updated = mapper.update(ownerId, linkClipId, request.version(), clip);
        if (updated == 0) {
            throw LinkClipApiException.conflict("他の端末で更新されています。再読込してください。");
        }
        replaceTags(ownerId, linkClipId, request.tags());
        return new LinkClipModels.Saved(rowOf(ownerId, linkClipId));
    }

    @Transactional
    public LinkClipModels.Deleted delete(UserPrincipal user, long linkClipId) {
        long ownerId = user.accountId();
        requireClip(ownerId, linkClipId);
        // タグは親の CASCADE でも消えるが、所有者確認後に明示的に消す（2.0 と同じ順序）。
        mapper.deleteTags(linkClipId);
        mapper.delete(ownerId, linkClipId);
        return new LinkClipModels.Deleted(true);
    }

    @Transactional
    public LinkClipModels.Saved updateFlags(UserPrincipal user, long linkClipId, LinkClipModels.FlagsRequest request) {
        long ownerId = user.accountId();
        requireClip(ownerId, linkClipId);
        if (request.favorite() == null && request.read() == null && request.archived() == null) {
            throw LinkClipApiException.invalid("変更する項目がありません。");
        }
        mapper.updateFlags(ownerId, linkClipId, request.favorite(), request.read(), request.archived());
        return new LinkClipModels.Saved(rowOf(ownerId, linkClipId));
    }

    /** リンクを開いたときに閲覧回数・最終閲覧日時を更新し、既読にする（2.0 と同じ）。 */
    @Transactional
    public LinkClipModels.Saved recordView(UserPrincipal user, long linkClipId) {
        long ownerId = user.accountId();
        requireClip(ownerId, linkClipId);
        mapper.touchView(ownerId, linkClipId);
        return new LinkClipModels.Saved(rowOf(ownerId, linkClipId));
    }

    /** 同じ URL の既存クリップ（重複候補）。2.0 同様、登録自体は禁止しない。 */
    @Transactional(readOnly = true)
    public LinkClipModels.DuplicateCheck duplicates(UserPrincipal user, String url) {
        long ownerId = user.accountId();
        String normalized = LinkClipUrls.normalize(url);
        if (normalized.isEmpty()) return new LinkClipModels.DuplicateCheck(false, List.of());
        List<LinkClipEntity> found = mapper.findByNormalizedUrl(ownerId, normalized);
        attachTags(ownerId, found);
        return new LinkClipModels.DuplicateCheck(!found.isEmpty(), found.stream().map(this::toRow).toList());
    }

    // ---------- 内部処理 ----------

    private LinkClipEntity buildClip(long ownerId, String url, String pageTitle, String siteName, String folderCode,
                                     String sourceCode, String clipType, String summary, String aiSummary,
                                     String memo, String publisherName, Timestamp publishedAt,
                                     Integer videoSeconds, String thumbnailUrl, Boolean favorite, Boolean archived) {
        String rawUrl = trimToNull(url);
        if (rawUrl == null) throw LinkClipApiException.invalid("URLを入力してください。");
        String normalized = LinkClipUrls.normalize(rawUrl);
        if (normalized.isEmpty()) normalized = rawUrl;
        String source = firstNonNull(normalizeFilter(sourceCode, SOURCES), LinkClipUrls.detectSourceCode(rawUrl));
        String type = firstNonNull(normalizeFilter(clipType, CLIP_TYPES), LinkClipUrls.defaultClipType(source));
        if (videoSeconds != null && videoSeconds < 0) {
            throw LinkClipApiException.invalid("動画時間は0以上で指定してください。");
        }

        // メタ情報は保存のたびに取得し直す（2.0 と同じ。取得できなければ入力値を優先）。
        LinkClipMetadataFetcher.Metadata metadata = metadataFetcher.fetch(rawUrl);

        LinkClipEntity clip = new LinkClipEntity();
        clip.setOwnerAccountId(ownerId);
        clip.setUrl(rawUrl);
        clip.setNormalizedUrl(normalized);
        clip.setSourceCode(source);
        clip.setClipType(type);
        clip.setFolderCode(firstNonNull(normalizeFilter(folderCode, FOLDERS), "INBOX"));
        clip.setSiteName(firstNonNull(trimToNull(siteName), metadata.siteName(),
                LinkClipUrls.hostOf(rawUrl), LinkClipUrls.isLocalPath(rawUrl) ? "ローカルファイル" : null));
        clip.setPageTitle(resolveTitle(pageTitle, metadata, rawUrl, clip.getSiteName()));
        clip.setSummary(firstNonNull(trimToNull(summary), metadata.description()));
        clip.setAiSummary(trimToNull(aiSummary));
        clip.setMemo(trimToNull(memo));
        clip.setPublisherName(firstNonNull(trimToNull(publisherName), metadata.publisherName(),
                LinkClipUrls.hostOf(rawUrl)));
        clip.setPublishedAt(firstNonNullTimestamp(publishedAt, metadata.publishedAt()));
        clip.setVideoSeconds(videoSeconds != null ? videoSeconds : metadata.videoSeconds());
        clip.setThumbnailUrl(firstNonNull(trimToNull(thumbnailUrl), metadata.thumbnailUrl()));
        clip.setFavorite(Boolean.TRUE.equals(favorite));
        clip.setRead(false);
        clip.setArchived(Boolean.TRUE.equals(archived));
        clip.setViewCount(0);
        clip.setMetaFetchedAt(metadata == null ? null : new Timestamp(System.currentTimeMillis()));
        return clip;
    }

    /** タイトルは 入力 → og:title → ローカルファイル名 → "{host} の保存リンク" の順で決める（2.0 と同じ）。 */
    private String resolveTitle(String pageTitle, LinkClipMetadataFetcher.Metadata metadata, String url, String siteName) {
        String manual = trimToNull(pageTitle);
        if (manual != null) return manual;
        if (metadata != null && metadata.title() != null) return truncate(metadata.title(), 300);
        if (LinkClipUrls.isLocalPath(url)) return truncate(LinkClipUrls.localFileName(url), 300);
        String host = LinkClipUrls.hostOf(url);
        if (!host.isEmpty()) return host + " の保存リンク";
        return "保存リンク";
    }

    /** タグは全置換。空除去・100文字上限・大文字小文字を無視した重複排除（2.0 と同じ）。 */
    private void replaceTags(long ownerId, long linkClipId, List<String> tags) {
        mapper.deleteTags(linkClipId);
        List<String> normalized = normalizeTags(tags);
        int order = 0;
        for (String tag : normalized) {
            mapper.insertTag(linkClipId, tag, order++, ownerId);
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : tags) {
            if (raw == null) continue;
            String tag = raw.trim();
            if (tag.isEmpty()) continue;
            if (tag.length() > MAX_TAG_LENGTH) tag = tag.substring(0, MAX_TAG_LENGTH);
            unique.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        }
        return new ArrayList<>(unique.values());
    }

    private void attachTags(long ownerId, List<LinkClipEntity> clips) {
        if (clips.isEmpty()) return;
        List<Long> ids = clips.stream().map(LinkClipEntity::getLinkClipId).toList();
        Map<Long, List<LinkClipEntity.TagRow>> byClip = new LinkedHashMap<>();
        for (LinkClipEntity.TagRow tag : mapper.findTagsByClipIds(ownerId, ids)) {
            byClip.computeIfAbsent(tag.getLinkClipId(), key -> new ArrayList<>()).add(tag);
        }
        for (LinkClipEntity clip : clips) {
            clip.setTags(byClip.getOrDefault(clip.getLinkClipId(), List.of()));
        }
    }

    private LinkClipEntity requireClip(long ownerId, long linkClipId) {
        LinkClipEntity clip = mapper.findById(ownerId, linkClipId);
        if (clip == null) throw LinkClipApiException.notFound();
        return clip;
    }

    /** 1 件を返す応答用（タグを紐付けてから行へ変換する）。 */
    private LinkClipModels.Row rowOf(long ownerId, long linkClipId) {
        LinkClipEntity clip = requireClip(ownerId, linkClipId);
        attachTags(ownerId, List.of(clip));
        return toRow(clip);
    }

    private LinkClipModels.Row toRow(LinkClipEntity clip) {
        List<String> tags = clip.getTags().stream().map(LinkClipEntity.TagRow::getTagName).toList();
        return new LinkClipModels.Row(clip.getLinkClipId(), clip.getFolderCode(), clip.getSourceCode(),
                clip.getClipType(), clip.getSiteName(), clip.getPageTitle(), clip.getUrl(), clip.getNormalizedUrl(),
                clip.getSummary(), clip.getAiSummary(), clip.getMemo(), clip.getPublisherName(),
                clip.getPublishedAt(), clip.getVideoSeconds(), clip.getThumbnailUrl(),
                clip.isFavorite(), clip.isRead(), clip.isArchived(),
                clip.getViewCount() == null ? 0 : clip.getViewCount(), clip.getLastViewedAt(),
                clip.getMetaFetchedAt(), clip.getAiSummarizedAt(),
                clip.getVersion() == null ? 1 : clip.getVersion(), tags, clip.getCreatedAt(), clip.getUpdatedAt());
    }

    private String normalizeFilter(String value, Set<String> allowed) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : null;
    }

    private Timestamp firstNonNullTimestamp(Timestamp primary, Timestamp fallback) {
        return primary != null ? primary : fallback;
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
