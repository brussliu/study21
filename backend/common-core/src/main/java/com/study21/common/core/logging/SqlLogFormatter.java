package com.study21.common.core.logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * DB 操作ログの 1 行フォーマット（純粋関数のみ・テストしやすいように分離）。
 *
 * <p>出力例：</p>
 * <pre>
 * op=SELECT mapper=com.study21.user.account.AccountMapper.findByLoginId duration=12ms rows=1 params=[loginId=foo] sql=SELECT ... WHERE login_id = ?
 * op=UPDATE mapper=... duration=8ms rows=2 status=FAILED error=org.postgresql.util.PSQLException: ERROR: duplicate key ... params=[...] sql=UPDATE ...
 * </pre>
 *
 * <p>1 操作 = 1 行を守るため、SQL とパラメータ値の改行・タブは空白へ潰す。</p>
 */
public final class SqlLogFormatter {

    /** SQL 本文の最大長（これを超えたら切り詰める）。 */
    static final int MAX_SQL_LENGTH = 4000;
    /** 1 パラメータ値の最大長。 */
    static final int MAX_VALUE_LENGTH = 200;

    private SqlLogFormatter() {
    }

    /** 複数行の SQL を 1 行に潰して返す。 */
    public static String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return truncate(flatten(sql), MAX_SQL_LENGTH);
    }

    /** 成功した DB 操作の 1 行を作る。 */
    public static String success(String mapper, String commandType, String sql,
                                 List<String> parameters, long durationMillis, int rowCount) {
        return new StringBuilder(256)
                .append("op=").append(commandType)
                .append(" mapper=").append(mapper)
                .append(" duration=").append(durationMillis).append("ms")
                .append(" rows=").append(rowCount)
                .append(" params=").append(formatParameters(parameters))
                .append(" sql=").append(sql)
                .toString();
    }

    /** 失敗した DB 操作の 1 行を作る（例外の詳細は error.log 側にスタックトレースで残す）。 */
    public static String failure(String mapper, String commandType, String sql,
                                 List<String> parameters, long durationMillis, Throwable error) {
        return new StringBuilder(256)
                .append("op=").append(commandType)
                .append(" mapper=").append(mapper)
                .append(" duration=").append(durationMillis).append("ms")
                .append(" rows=-")
                .append(" status=FAILED")
                .append(" error=").append(describeError(error))
                .append(" params=").append(formatParameters(parameters))
                .append(" sql=").append(sql)
                .toString();
    }

    /**
     * バインドパラメータを {@code name=value} の一覧にする。
     *
     * <p>Mapper メソッドの引数（{@code @Param} 付きの複数引数＝Map、エンティティ 1 件、
     * 単一のプリミティブ）を同じ形で扱えるようにしている。</p>
     */
    public static List<String> describeParameters(BoundSql boundSql, Object parameterObject) {
        List<String> described = new ArrayList<>();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings == null) {
            return described;
        }
        for (ParameterMapping mapping : mappings) {
            if (mapping.getMode() == ParameterMode.OUT) {
                continue;
            }
            String property = mapping.getProperty();
            Object value = resolveValue(boundSql, parameterObject, property, mappings.size());
            described.add(property + "=" + describeValue(value));
        }
        return described;
    }

    private static Object resolveValue(BoundSql boundSql, Object parameterObject, String property, int mappingCount) {
        if (boundSql.hasAdditionalParameter(property)) {
            return boundSql.getAdditionalParameter(property);
        }
        if (parameterObject == null) {
            return null;
        }
        if (parameterObject instanceof Map<?, ?> map) {
            if (map.containsKey(property)) {
                return map.get(property);
            }
            int dot = property.indexOf('.');
            if (dot > 0) {
                Object root = map.get(property.substring(0, dot));
                return readProperty(root, property.substring(dot + 1));
            }
            // 単一引数を Map として包んでいる場合（param1 / value 等）はその値を返す
            return mappingCount == 1 ? map.values().stream().findFirst().orElse(null) : null;
        }
        Object direct = readProperty(parameterObject, property);
        if (direct != null) {
            return direct;
        }
        // @Param 無しの単一プリミティブ引数（プロパティ名が value / param1 になる）
        return mappingCount == 1 ? parameterObject : null;
    }

    private static Object readProperty(Object target, String property) {
        if (target == null || property == null || property.isBlank()) {
            return null;
        }
        try {
            MetaObject meta = SystemMetaObject.forObject(target);
            return meta.hasGetter(property) ? meta.getValue(property) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String formatParameters(List<String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "[]";
        }
        return "[" + String.join(", ", parameters) + "]";
    }

    static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[] bytes) {
            return "<bytes:" + bytes.length + ">";
        }
        if (value instanceof Collection<?> collection) {
            return "<collection:" + collection.size() + ">";
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) {
            return "''";
        }
        return truncate(flatten(text), MAX_VALUE_LENGTH);
    }

    private static String describeError(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage() == null ? "" : ": " + error.getMessage();
        return truncate(flatten(error.getClass().getName() + message), MAX_VALUE_LENGTH);
    }

    /** 改行・タブ・連続空白を 1 つの空白に潰す（1 操作 1 行を守るため）。 */
    private static String flatten(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...<truncated>";
    }
}
