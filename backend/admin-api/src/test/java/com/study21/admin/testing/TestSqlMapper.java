package com.study21.admin.testing;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * **テスト専用**の SQL 実行 Mapper（DDL・生データの確認・後始末に使う）。
 *
 * <p>移行スクリプトの検証や、テストが作ったデータの片付けなど、Mapper の定型では書けない
 * SQL を流すために使う（{@code ${sql}}）。テストからしか使わないが、**MyBatis 経由**なので
 * 既存の SQL ログ（{@code SqlLoggingInterceptor}）にも記録される。</p>
 */
@Mapper
public interface TestSqlMapper {

    /** 任意の DDL / DML を 1 文だけ実行する。 */
    @Update("${sql}")
    int execute(@Param("sql") String sql);

    /** 任意の SELECT を実行する。 */
    @Select("${sql}")
    List<Map<String, Object>> query(@Param("sql") String sql);
}
