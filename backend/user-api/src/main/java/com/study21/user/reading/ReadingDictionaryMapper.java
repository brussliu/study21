package com.study21.user.reading;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * RED_語彙辞書情報（語彙・読みの引き当て結果のキャッシュ）の Mapper。
 *
 * <p>同じ語を何度も外部へ引きに行かないための表。`UNIQUE (言語, 見出し語)` なので
 * 登録は {@link #upsert(ReadingDictionaryEntity)}（`ON CONFLICT DO UPDATE`）だけを使う。</p>
 */
@Mapper
public interface ReadingDictionaryMapper {

    /** 正規化済みの見出し語でキャッシュを引く。無ければ null。 */
    ReadingDictionaryEntity find(@Param("language") String language, @Param("headword") String headword);

    /**
     * 引き当て結果を登録・更新する（`ON CONFLICT (言語, 見出し語) DO UPDATE`）。
     * 今回取れなかった項目は既存の値を残す（COALESCE）。
     */
    int upsert(ReadingDictionaryEntity entity);

    /** 1 語を消す（テスト・手直し用）。 */
    int delete(@Param("language") String language, @Param("headword") String headword);
}
