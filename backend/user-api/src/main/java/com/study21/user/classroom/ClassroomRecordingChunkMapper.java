package com.study21.user.classroom;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * CR_授業録音分塊情報（録音の分塊）の Mapper（user-api 側）。
 *
 * <p>同じ連番の再送・同時再送で**行が 2 つできない**ように、登録は
 * {@code INSERT ... ON CONFLICT DO NOTHING} を使う（一意制約違反の例外にしない。
 * PostgreSQL は 1 文が失敗するとトランザクション全体が中断し、後続の照会もできなくなるため）。</p>
 */
@Mapper
public interface ClassroomRecordingChunkMapper {

    /** 分塊を登録する（同じ連番があれば**何もしない**。戻り値 0 = 既にあった）。 */
    int insertIfAbsent(ClassroomRecordingChunkEntity entity);

    /** 処理状態と、この分塊から作った転写セグメントの数を書き換える。 */
    int updateStatus(@Param("recordId") long recordId,
                     @Param("seq") int seq,
                     @Param("status") String status,
                     @Param("segmentCount") int segmentCount);

    /** 同じ連番の分塊（無ければ null）。冪等判定に使う。 */
    ClassroomRecordingChunkEntity findBySeq(@Param("recordId") long recordId, @Param("seq") int seq);

    /** 指定連番より後の分塊（連番順）。 */
    List<ClassroomRecordingChunkEntity> findByRecordAfter(@Param("recordId") long recordId,
                                                          @Param("afterSeq") int afterSeq);

    /** その記録の全分塊（連番順）。 */
    List<ClassroomRecordingChunkEntity> findByRecord(@Param("recordId") long recordId);

    /** その記録の最大連番（無ければ null）。「次に送る連番」の材料。 */
    Integer maxSeq(@Param("recordId") long recordId);

    /** その記録の分塊の数。 */
    int countByRecord(@Param("recordId") long recordId);

    /** 保存済みのバイト数の合計（0 件なら 0）。 */
    long totalBytes(@Param("recordId") long recordId);

    /**
     * 保存済みの分塊が示す**録音の位置（秒）**。無ければ null。
     *
     * <p>録音の最大時間の判定と、開き直したあとの続きの位置に使う。
     * 転写セグメントの連番（＝文の数）では測らない（無音の授業では 0 のままになる）。</p>
     */
    BigDecimal maxEndOffsetSeconds(@Param("recordId") long recordId);
}
