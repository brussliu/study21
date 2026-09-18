package com.study21.user.geometry;

/** 図形の集計行（一覧のサマリ）。 */
public class GeometryTotalsEntity {

    private Long figureCount;
    private Long geometryCount;
    private Long functionCount;
    private Long deletedCount;

    public long figures() { return figureCount == null ? 0L : figureCount; }
    public long geometry() { return geometryCount == null ? 0L : geometryCount; }
    public long functions() { return functionCount == null ? 0L : functionCount; }
    public long deleted() { return deletedCount == null ? 0L : deletedCount; }

    public void setFigureCount(Long figureCount) { this.figureCount = figureCount; }
    public void setGeometryCount(Long geometryCount) { this.geometryCount = geometryCount; }
    public void setFunctionCount(Long functionCount) { this.functionCount = functionCount; }
    public void setDeletedCount(Long deletedCount) { this.deletedCount = deletedCount; }
}
