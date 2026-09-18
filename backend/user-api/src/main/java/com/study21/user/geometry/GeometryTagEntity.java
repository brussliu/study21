package com.study21.user.geometry;

/** タグの候補（タグと件数）。 */
public class GeometryTagEntity {

    private String tag;
    private Long tagCount;

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public Long getTagCount() { return tagCount; }
    public void setTagCount(Long tagCount) { this.tagCount = tagCount; }
}
