package com.study21.admin.migration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * **移行スクリプトの検証用**の Mapper（テスト専用。業務では使わない）。
 *
 * <p>移行は DDL と PL/pgSQL なので、Mapper の定型では書けない。ここだけは任意の SQL を
 * そのまま流す（{@code ${sql}}）。テストからしか使わないが、**MyBatis 経由**なので
 * 既存の SQL ログ（{@code SqlLoggingInterceptor}）にも記録される。</p>
 */
@Mapper
public interface MigrationTestMapper {

    /** 任意の DDL / DML を 1 文だけ実行する。 */
    @Update("${sql}")
    int execute(@Param("sql") String sql);

    /** 任意の SELECT を実行する（移行後の構造・データの確認用）。 */
    @Select("${sql}")
    List<Map<String, Object>> query(@Param("sql") String sql);
}
