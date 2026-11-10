package com.bluewhale.agent.support.dataquery;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读 SQL 守卫：单条 SELECT、白名单视图、强制行数上限。
 *
 * <p>校验顺序：去注释 → 拒绝多语句 → 仅允许 {@code SELECT} 开头 → 拒绝危险关键字/函数/系统库
 * → 校验 {@code FROM}/{@code JOIN} 的全部标识符均在白名单内 → 追加或裁剪 {@code LIMIT}。
 * 不依赖第三方 SQL 解析器，规则保守：宁可拒绝，不放行可疑语句。</p>
 *
 * @author BlueWhale
 */
public final class DataQueryGuard {

    /** SQL 最大长度 */
    private static final int MAX_SQL_CHARS = 4000;

    /** 注释：块注释、{@code --}、{@code #} */
    private static final Pattern COMMENT = Pattern.compile("(?s)/\\*.*?\\*/|--[^\\n]*|#[^\\n]*");

    /** 危险关键字与系统库 */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|grant|revoke|call|exec|execute|merge|replace"
                    + "|rename|load|outfile|dumpfile|information_schema|performance_schema|mysql|sys)\\b");

    /** 危险函数调用（时延攻击与文件读取） */
    private static final Pattern FORBIDDEN_CALL = Pattern.compile("(?i)\\b(sleep|benchmark|load_file)\\s*\\(");

    /** {@code FROM}/{@code JOIN} 后的标识符（可带库名与反引号） */
    private static final Pattern TABLE = Pattern.compile("(?i)\\b(?:from|join)\\s+([`\\w.]+)");

    /** 末尾 {@code LIMIT}（支持 {@code LIMIT n} 与 {@code LIMIT offset, n}） */
    private static final Pattern TRAILING_LIMIT = Pattern.compile("(?i)\\blimit\\s+(\\d+)(?:\\s*,\\s*(\\d+))?\\s*$");

    /** 任意位置的 LIMIT 关键字（用于拒绝非末尾 LIMIT） */
    private static final Pattern ANY_LIMIT = Pattern.compile("(?i)\\blimit\\b");

    /** 工具类不允许实例化 */
    private DataQueryGuard() {
    }

    /**
     * 校验并规范化 SQL。
     *
     * @param sql         原始 SQL
     * @param allowedViews 允许访问的视图（库内视图名，忽略大小写）
     * @param maxRows     行数上限
     * @return 规范化后的 SQL 与实际访问的视图
     */
    public static GuardedQuery check(String sql, List<String> allowedViews, int maxRows) {
        String normalized = normalize(sql);
        verifyTables(normalized, allowedViews);
        String limited = applyLimit(normalized, Math.max(1, maxRows));
        return new GuardedQuery(limited, extractTables(limited));
    }

    /**
     * 去注释、去尾部单分号，并做基础形态校验。
     *
     * @param sql 原始 SQL
     * @return 规范化 SQL
     */
    private static String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw reject("SQL 不能为空");
        }
        if (sql.length() > MAX_SQL_CHARS) {
            throw reject("SQL 过长（上限 " + MAX_SQL_CHARS + " 字符）");
        }
        String normalized = COMMENT.matcher(sql).replaceAll(" ").trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.contains(";")) {
            throw reject("仅允许单条 SQL，禁止多语句");
        }
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("select")) {
            throw reject("仅允许 SELECT 查询");
        }
        Matcher forbidden = FORBIDDEN.matcher(normalized);
        if (forbidden.find()) {
            throw reject("SQL 包含非法关键字：" + forbidden.group());
        }
        if (FORBIDDEN_CALL.matcher(normalized).find()) {
            throw reject("SQL 包含非法函数调用");
        }
        return normalized;
    }

    /**
     * 校验所有 {@code FROM}/{@code JOIN} 的表/视图都在白名单内。
     *
     * @param sql          规范化 SQL
     * @param allowedViews 白名单视图
     */
    private static void verifyTables(String sql, List<String> allowedViews) {
        Set<String> allowed = new LinkedHashSet<>();
        if (allowedViews != null) {
            allowedViews.stream()
                    .filter(view -> view != null && !view.isBlank())
                    .map(view -> view.trim().toLowerCase(Locale.ROOT))
                    .forEach(allowed::add);
        }
        List<String> tables = extractTables(sql);
        if (tables.isEmpty()) {
            throw reject("查询必须访问白名单视图");
        }
        for (String table : tables) {
            if (!allowed.contains(table)) {
                throw reject("未授权的视图：" + table);
            }
        }
    }

    /**
     * 追加或裁剪 LIMIT。
     *
     * @param sql     规范化 SQL
     * @param maxRows 行数上限
     * @return 带 LIMIT 的 SQL
     */
    private static String applyLimit(String sql, int maxRows) {
        Matcher trailing = TRAILING_LIMIT.matcher(sql);
        if (trailing.find()) {
            int rows = Integer.parseInt(trailing.group(2) != null ? trailing.group(2) : trailing.group(1));
            if (rows > maxRows) {
                return sql.substring(0, trailing.start()) + "LIMIT " + maxRows;
            }
            return sql;
        }
        if (ANY_LIMIT.matcher(sql).find()) {
            throw reject("LIMIT 仅允许出现在语句末尾");
        }
        return sql + " LIMIT " + maxRows;
    }

    /**
     * 提取 SQL 中所有表/视图的末段名称（小写）。
     *
     * @param sql SQL
     * @return 去重后的视图名
     */
    private static List<String> extractTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE.matcher(sql);
        while (matcher.find()) {
            String identifier = matcher.group(1).replace("`", "");
            String[] segments = identifier.split("\\.");
            tables.add(segments[segments.length - 1].toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(tables);
    }

    /**
     * 构造 403 业务异常（数据通道安全策略拒绝）。
     *
     * @param message 原因
     * @return 业务异常
     */
    private static BizException reject(String message) {
        return new BizException(ErrorCode.FORBIDDEN.getCode(), message);
    }

    /**
     * 校验结果。
     *
     * @param sql   已追加/裁剪 LIMIT 的 SQL
     * @param views 实际访问的视图
     * @author BlueWhale
     */
    public record GuardedQuery(String sql, List<String> views) {
    }
}
