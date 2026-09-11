package com.study21.user.linkclip;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** LNK_リンククリップ情報 の 1 行（タグは集約内の子情報として保持する）。 */
public class LinkClipEntity {
    private Long linkClipId;
    private Long ownerAccountId;
    private String folderCode;
    private String sourceCode;
    private String clipType;
    private String siteName;
    private String pageTitle;
    private String url;
    private String normalizedUrl;
    private String summary;
    private String aiSummary;
    private String memo;
    private String publisherName;
    private Timestamp publishedAt;
    private Integer videoSeconds;
    private String thumbnailUrl;
    private boolean favorite;
    private boolean read;
    private boolean archived;
    private Integer viewCount;
    private Timestamp lastViewedAt;
    private Timestamp metaFetchedAt;
    private Timestamp aiSummarizedAt;
    private Integer version;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<TagRow> tags = new ArrayList<>();

    /** 表示順つきのタグ（MyBatis の自動マッピング用に setter を持つ）。 */
    public static class TagRow {
        private Long linkClipId;
        private String tagName;
        private Integer displayOrder;

        public Long getLinkClipId() { return linkClipId; }
        public void setLinkClipId(Long linkClipId) { this.linkClipId = linkClipId; }
        public String getTagName() { return tagName; }
        public void setTagName(String tagName) { this.tagName = tagName; }
        public Integer getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    }

    public Long getLinkClipId() { return linkClipId; }
    public void setLinkClipId(Long linkClipId) { this.linkClipId = linkClipId; }
    public Long getOwnerAccountId() { return ownerAccountId; }
    public void setOwnerAccountId(Long ownerAccountId) { this.ownerAccountId = ownerAccountId; }
    public String getFolderCode() { return folderCode; }
    public void setFolderCode(String folderCode) { this.folderCode = folderCode; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getClipType() { return clipType; }
    public void setClipType(String clipType) { this.clipType = clipType; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getNormalizedUrl() { return normalizedUrl; }
    public void setNormalizedUrl(String normalizedUrl) { this.normalizedUrl = normalizedUrl; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }
    public Timestamp getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Timestamp publishedAt) { this.publishedAt = publishedAt; }
    public Integer getVideoSeconds() { return videoSeconds; }
    public void setVideoSeconds(Integer videoSeconds) { this.videoSeconds = videoSeconds; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Timestamp getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(Timestamp lastViewedAt) { this.lastViewedAt = lastViewedAt; }
    public Timestamp getMetaFetchedAt() { return metaFetchedAt; }
    public void setMetaFetchedAt(Timestamp metaFetchedAt) { this.metaFetchedAt = metaFetchedAt; }
    public Timestamp getAiSummarizedAt() { return aiSummarizedAt; }
    public void setAiSummarizedAt(Timestamp aiSummarizedAt) { this.aiSummarizedAt = aiSummarizedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    public List<TagRow> getTags() { return tags; }
    public void setTags(List<TagRow> tags) { this.tags = tags == null ? new ArrayList<>() : tags; }
}
