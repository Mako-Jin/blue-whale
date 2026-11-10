package com.bluewhale.agent.support.facts;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DDL 事实采集器：解析各服务 {@code db/init.sql} 的 {@code CREATE TABLE} 语句。
 *
 * <p>产出 {@code TABLE}（表名、所属模块、列数、注释）与 {@code COLUMN}
 * （表名.列名、类型、是否可空、注释）两类事实，供「某表有哪些字段」类问题精确回答。</p>
 *
 * @author BlueWhale
 */
@Component
public class DdlFactCollector implements FactCollector {

    /** CREATE TABLE 语句（含表体与引擎尾部） */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 表注释 */
    private static final Pattern TABLE_COMMENT = Pattern.compile(
            "COMMENT\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);

    /** 列名（行首反引号或普通标识符） */
    private static final Pattern COLUMN = Pattern.compile("^`?([A-Za-z0-9_]+)`?\\s+([A-Za-z]+(?:\\([^)]*\\))?)");

    /** 列注释 */
    private static final Pattern COLUMN_COMMENT = Pattern.compile("COMMENT\\s+'([^']*)'", Pattern.CASE_INSENSITIVE);

    @Override
    public List<FactRecord> collect(Path repoRoot) {
        List<FactRecord> facts = new ArrayList<>();
        List<Path> files = FactFileSupport.walk(repoRoot.resolve("whale-backend"),
                path -> "init.sql".equals(path.getFileName().toString())
                        && path.toString().replace('\\', '/').contains("/db/"));
        for (Path file : files) {
            collectFile(repoRoot, file, facts);
        }
        return facts;
    }

    /**
     * 解析单个 DDL 文件。
     *
     * @param repoRoot 仓库根
     * @param file     DDL 文件
     * @param facts    事实收集容器
     */
    private void collectFile(Path repoRoot, Path file, List<FactRecord> facts) {
        String text = FactFileSupport.read(file);
        if (text.isEmpty()) {
            return;
        }
        String sourcePath = FactFileSupport.relative(repoRoot, file);
        String module = moduleOf(sourcePath);
        Matcher tableMatcher = CREATE_TABLE.matcher(text);
        while (tableMatcher.find()) {
            String table = tableMatcher.group(1);
            String body = tableMatcher.group(2);
            Map<String, Object> tableValue = new LinkedHashMap<>();
            tableValue.put("module", module);
            tableValue.put("source", sourcePath);
            List<Map<String, Object>> columns = parseColumns(body);
            tableValue.put("columnCount", columns.size());
            tableValue.put("columns", columns);
            tableValue.put("comment", tableComment(text, tableMatcher.end()));
            facts.add(new FactRecord("TABLE", table, tableValue, sourcePath));
            for (Map<String, Object> column : columns) {
                Map<String, Object> columnValue = new LinkedHashMap<>();
                columnValue.put("table", table);
                columnValue.put("type", column.get("type"));
                columnValue.put("nullable", column.get("nullable"));
                columnValue.put("comment", column.get("comment"));
                columnValue.put("module", module);
                facts.add(new FactRecord("COLUMN", table + "." + column.get("name"),
                        columnValue, sourcePath));
            }
        }
    }

    /**
     * 解析表体中的列定义。
     *
     * @param body 表体
     * @return 列元数据列表
     */
    private List<Map<String, Object>> parseColumns(String body) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || isConstraint(line)) {
                continue;
            }
            Matcher matcher = COLUMN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("name", matcher.group(1));
            column.put("type", matcher.group(2).toUpperCase());
            column.put("nullable", !line.toUpperCase().contains("NOT NULL"));
            Matcher comment = COLUMN_COMMENT.matcher(line);
            column.put("comment", comment.find() ? comment.group(1) : "");
            columns.add(column);
        }
        return columns;
    }

    /**
     * 判断是否为约束/索引定义行。
     *
     * @param line 行内容
     * @return 是约束行返回 {@code true}
     */
    private static boolean isConstraint(String line) {
        String upper = line.toUpperCase();
        return upper.startsWith("PRIMARY KEY") || upper.startsWith("UNIQUE KEY")
                || upper.startsWith("KEY ") || upper.startsWith("KEY`")
                || upper.startsWith("INDEX ") || upper.startsWith("CONSTRAINT")
                || upper.startsWith("FOREIGN KEY") || upper.startsWith("FULLTEXT");
    }

    /**
     * 读取表注释（CREATE TABLE 尾部最近的一条 {@code COMMENT='...'}）。
     *
     * @param text  DDL 全文
     * @param start CREATE TABLE 结束位置
     * @return 表注释；无则空串
     */
    private static String tableComment(String text, int start) {
        int end = text.indexOf(';', start);
        String tail = end > start ? text.substring(start, end) : text.substring(start);
        Matcher matcher = TABLE_COMMENT.matcher(tail);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 从路径提取模块名（{@code whale-backend/whale-services/whale-form/...} → {@code whale-form}）。
     *
     * @param path 相对路径
     * @return 模块名
     */
    private static String moduleOf(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].startsWith("whale-")) {
                return parts[i];
            }
        }
        return "";
    }
}
