package com.study21.user.geometry;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 図形管理の実装（2.0 の `geometry.jsp` / `geometry_draw.jsp` を作り直したもの）。
 *
 * <p>2.0 から引き継いだ点:</p>
 * <ul>
 *   <li>GeoGebra の作図データ（XML）とサムネイル（Base64 PNG）を 1 行に持つ</li>
 *   <li>タグは `|` 区切り</li>
 *   <li>削除は論理削除（2.0 の 削除FLG='1' → 2.1 の 状態コード='DELETED'）</li>
 *   <li>図形の種類は geometry（幾何図形）/ function（関数グラフ）</li>
 * </ul>
 *
 * <p>2.1 で足した点: 楽観的ロック（バージョン）、監査列（誰が保存したか）、
 * 図形番号の採番（GEO + 日時 + 乱数）、表示順の自動採番。</p>
 */
@Service
public class GeometryServiceImpl implements GeometryService {

    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TAG_MAX_LENGTH = 60;
    private static final int TAG_MAX_COUNT = 20;
    private static final int MEMO_MAX_LENGTH = 2000;

    private final GeometryMapper geometryMapper;

    public GeometryServiceImpl(GeometryMapper geometryMapper) {
        this.geometryMapper = geometryMapper;
    }

    // ------------------------------------------------------------------ 一覧

    @Override
    @Transactional(readOnly = true)
    public GeometryModels.FigureListResult search(String keyword, String figureType, String tag, String sort,
                                                  boolean includeDeleted, int page, int size) {
        int safeSize = size <= 0 ? GeometryModels.DEFAULT_SIZE : Math.min(size, GeometryModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String typeFilter = normalizeType(figureType);
        String sortKey = normalizeSort(sort);
        String keywordFilter = blankToNull(keyword);
        String tagFilter = blankToNull(tag);

        long total = geometryMapper.count(keywordFilter, typeFilter, tagFilter, includeDeleted);
        List<GeometryModels.FigureRow> items = geometryMapper.search(keywordFilter, typeFilter, tagFilter,
                        includeDeleted, sortKey, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(GeometryServiceImpl::toRow)
                .toList();
        GeometryTotalsEntity totals = geometryMapper.totals();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new GeometryModels.FigureListResult(items, total, safePage, safeSize, totalPages,
                new GeometryModels.GeometryTotals(totals.figures(), totals.geometry(), totals.functions(),
                        totals.deleted()));
    }

    @Override
    @Transactional(readOnly = true)
    public GeometryModels.FigureDetail detail(long figureId) {
        GeometryEntity entity = requireFigure(figureId);
        return new GeometryModels.FigureDetail(toRow(entity), entity.getConstruction(), entity.getThumbnail());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeometryModels.TagRow> tags(boolean includeDeleted) {
        return geometryMapper.tagSuggestions(includeDeleted).stream()
                .map(entity -> new GeometryModels.TagRow(entity.getTag(),
                        entity.getTagCount() == null ? 0L : entity.getTagCount()))
                .toList();
    }

    /** サムネイルの PNG（一覧の img src に使う）。無ければ null。 */
    @Override
    @Transactional(readOnly = true)
    public byte[] thumbnail(long figureId) {
        GeometryEntity entity = requireFigure(figureId);
        String thumbnail = entity.getThumbnail();
        if (thumbnail == null || thumbnail.isBlank()) {
            return null;
        }
        String base64 = thumbnail.contains(",") ? thumbnail.substring(thumbnail.indexOf(',') + 1) : thumbnail;
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException cause) {
            return null;
        }
    }

    // ------------------------------------------------------------ 登録・更新

    @Override
    @Transactional
    public GeometryModels.FigureMutationResult create(UserPrincipal user, GeometryModels.FigureSaveRequest request) {
        return create(user, request, "APP");
    }

    /**
     * 図形の保存（新規。登録元コードを指定する）。
     *
     * <p>AI 生図から作った図形は `'AI'`（`GEO_AI生図リクエスト情報.図形ID` で要求と結び付く）。
     * それ以外の入口（画面からの保存・コピー）は `'APP'`。</p>
     */
    @Override
    @Transactional
    public GeometryModels.FigureMutationResult create(UserPrincipal user, GeometryModels.FigureSaveRequest request,
                                                      String sourceCode) {
        GeometryEntity entity = new GeometryEntity();
        entity.setFigureNo(nextFigureNo());
        entity.setSubject("数学");
        entity.setFigureType(choiceOrDefault(request.figureType(), GeometryModels.FIGURE_TYPES, "図形の種類",
                GeometryModels.TYPE_GEOMETRY));
        entity.setKind("saved");
        entity.setTitle(request.title().trim());
        entity.setMemo(trimToNull(request.memo(), MEMO_MAX_LENGTH));
        entity.setTags(formatTags(request.tags()));
        entity.setConstruction(limit(request.construction(), GeometryModels.CONSTRUCTION_MAX, "作図データ"));
        entity.setThumbnail(limit(request.thumbnail(), GeometryModels.THUMBNAIL_MAX, "サムネイル"));
        entity.setDisplayOrder(geometryMapper.nextDisplayOrder());
        entity.setSourceCode(blankToNull(sourceCode) == null ? "APP" : sourceCode.trim());
        entity.setCreatedBy(user.accountId());
        geometryMapper.insert(entity);
        return new GeometryModels.FigureMutationResult(toRow(requireFigure(entity.getFigureId())),
                "図形を保存しました。（" + entity.getFigureNo() + "）");
    }

    @Override
    @Transactional
    public GeometryModels.FigureMutationResult update(UserPrincipal user, long figureId,
                                                      GeometryModels.FigureSaveRequest request) {
        GeometryEntity current = requireFigure(figureId);
        GeometryEntity entity = new GeometryEntity();
        entity.setFigureId(figureId);
        entity.setTitle(request.title().trim());
        entity.setFigureType(choiceOrDefault(request.figureType(), GeometryModels.FIGURE_TYPES, "図形の種類",
                current.getFigureType()));
        entity.setMemo(trimToNull(request.memo(), MEMO_MAX_LENGTH));
        entity.setTags(formatTags(request.tags()));
        entity.setConstruction(limit(request.construction(), GeometryModels.CONSTRUCTION_MAX, "作図データ"));
        entity.setThumbnail(limit(request.thumbnail(), GeometryModels.THUMBNAIL_MAX, "サムネイル"));
        entity.setUpdatedBy(user.accountId());
        entity.setVersion(request.version() == null ? current.getVersion() : request.version());
        if (geometryMapper.update(entity) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new GeometryModels.FigureMutationResult(toRow(requireFigure(figureId)), "図形を更新しました。");
    }

    @Override
    @Transactional
    public GeometryModels.FigureMutationResult duplicate(UserPrincipal user, long figureId) {
        GeometryEntity source = requireFigure(figureId);
        GeometryEntity entity = new GeometryEntity();
        entity.setFigureNo(nextFigureNo());
        entity.setSubject(source.getSubject());
        entity.setFigureType(source.getFigureType());
        entity.setKind("saved");
        entity.setTitle(copyTitle(source.getTitle()));
        entity.setMemo(source.getMemo());
        entity.setTags(source.getTags());
        entity.setConstruction(source.getConstruction());
        entity.setThumbnail(source.getThumbnail());
        entity.setDisplayOrder(geometryMapper.nextDisplayOrder());
        entity.setCreatedBy(user.accountId());
        geometryMapper.insert(entity);
        return new GeometryModels.FigureMutationResult(toRow(requireFigure(entity.getFigureId())),
                "図形をコピーしました。（" + entity.getFigureNo() + "）");
    }

    @Override
    @Transactional
    public GeometryModels.FigureMutationResult updateOrder(UserPrincipal user, long figureId, int displayOrder,
                                                           Integer version) {
        GeometryEntity current = requireFigure(figureId);
        int expected = version == null ? current.getVersion() : version;
        if (geometryMapper.updateDisplayOrder(figureId, displayOrder, user.accountId(), expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new GeometryModels.FigureMutationResult(toRow(requireFigure(figureId)),
                "表示順を " + displayOrder + " にしました。");
    }

    @Override
    @Transactional
    public GeometryModels.SimpleResult delete(UserPrincipal user, long figureId) {
        GeometryEntity current = requireFigure(figureId);
        if (GeometryModels.STATUS_DELETED.equals(current.getStatusCode())) {
            throw new ValidationException("この図形はすでに削除されています。");
        }
        geometryMapper.softDelete(figureId, user.accountId());
        return new GeometryModels.SimpleResult(1, "図形を削除しました。");
    }

    // -------------------------------------------------------------------- 内部

    private GeometryEntity requireFigure(long figureId) {
        GeometryEntity entity = geometryMapper.findById(figureId);
        if (entity == null) {
            throw new NotFoundException("図形が見つかりません。");
        }
        return entity;
    }

    /** 2.0 と同じ `GEO` + 日時 + 乱数。念のため重複を避ける。 */
    private String nextFigureNo() {
        for (int attempt = 0; attempt < 20; attempt += 1) {
            String candidate = "GEO" + LocalDateTime.now().format(NO_FORMAT) + (1000 + RANDOM.nextInt(9000));
            if (geometryMapper.findByNo(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("図形番号を採番できませんでした。");
    }

    /** コピーしたときの名前（2.0 には無かったが、元と区別できるようにする）。 */
    private static String copyTitle(String title) {
        String base = title + " のコピー";
        return base.length() > GeometryModels.TITLE_MAX ? base.substring(0, GeometryModels.TITLE_MAX) : base;
    }

    /** 図形の種類（空は絞り込み無し、不正はエラー）。 */
    private static String normalizeType(String figureType) {
        return choiceOrDefault(figureType, GeometryModels.FIGURE_TYPES, "図形の種類", null);
    }

    private static String normalizeSort(String sort) {
        String value = blankToNull(sort);
        if (value == null) {
            return "updatedDesc";
        }
        if (!GeometryModels.SORTS.contains(value)) {
            throw new ValidationException("並び替えは " + String.join(" / ", GeometryModels.SORTS)
                    + " のいずれかを指定してください。");
        }
        return value;
    }

    private static String choiceOrDefault(String value, List<String> allowed, String label, String fallback) {
        String text = blankToNull(value);
        if (text == null) {
            return fallback;
        }
        if (!allowed.contains(text)) {
            throw new ValidationException(label + "は " + String.join(" / ", allowed) + " のいずれかを指定してください。");
        }
        return text;
    }

    /** タグは `|` 区切りで保存する（2.0 と同じ形式）。カンマ区切りも受け付ける。 */
    private static String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = blankToNull(tag);
            if (value == null) {
                continue;
            }
            if (value.contains(GeometryModels.TAG_SEPARATOR)) {
                throw new ValidationException("タグに「" + GeometryModels.TAG_SEPARATOR + "」は使えません。");
            }
            if (value.length() > TAG_MAX_LENGTH) {
                throw new ValidationException("タグは" + TAG_MAX_LENGTH + "文字以内で入力してください。");
            }
            unique.add(value);
            if (unique.size() >= TAG_MAX_COUNT) {
                break;
            }
        }
        return unique.isEmpty() ? null : String.join(GeometryModels.TAG_SEPARATOR, unique);
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : tags.split("[|,]")) {
            String value = blankToNull(part);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static String limit(String value, int max, String label) {
        if (value == null) {
            return "";
        }
        if (value.length() > max) {
            throw new ValidationException(label + "が大きすぎます（" + max + " 文字まで）。");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimToNull(String value, int max) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }

    private static GeometryModels.FigureRow toRow(GeometryEntity entity) {
        String status = entity.getStatusCode() == null ? GeometryModels.STATUS_ACTIVE : entity.getStatusCode();
        int constructionLength = entity.getConstructionLength() != null
                ? entity.getConstructionLength()
                : (entity.getConstruction() == null ? 0 : entity.getConstruction().length());
        boolean hasThumbnail = entity.getHasThumbnail() != null
                ? Boolean.TRUE.equals(entity.getHasThumbnail())
                : entity.getThumbnail() != null && !entity.getThumbnail().isBlank();
        return new GeometryModels.FigureRow(
                entity.getFigureId() == null ? 0L : entity.getFigureId(),
                entity.getFigureNo(),
                entity.getSubject(),
                entity.getFigureType(),
                entity.getKind(),
                entity.getTitle(),
                entity.getMemo(),
                parseTags(entity.getTags()),
                entity.getDisplayOrder() == null ? 0 : entity.getDisplayOrder(),
                status,
                hasThumbnail,
                constructionLength,
                entity.getVersion() == null ? 1 : entity.getVersion(),
                iso(entity.getCreatedAt()),
                iso(entity.getUpdatedAt()));
    }
}
