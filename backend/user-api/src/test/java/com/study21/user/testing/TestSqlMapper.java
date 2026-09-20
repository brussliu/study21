package com.study21.user.testing;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * **テスト専用**の SQL 実行 Mapper（検証データの作成・後片付け・状態の確認に使う）。
 *
 * <p>定型の Mapper では書けない SQL（`RETURNING` つきの INSERT・後片付けの DELETE・状態の確認）を
 * 流すために使う。テストからしか使わないが、**MyBatis 経由**なので既存の SQL ログ
 * （{@code SqlLoggingInterceptor}）に記録される（JdbcTemplate のようにログを素通りしない）。</p>
 *
 * <p>**パスワードや合言葉は引数に取っても、ここからログへは出さない**（SQL ログは
 * プレースホルダの値までは出さない）。</p>
 */
@Mapper
public interface TestSqlMapper {

    /** 任意の SELECT を実行する（行を Map で受ける）。 */
    @Select("${sql}")
    List<Map<String, Object>> query(@Param("sql") String sql);

    /** 任意の DML を 1 文だけ実行する。 */
    @Update("${sql}")
    int execute(@Param("sql") String sql);
}
