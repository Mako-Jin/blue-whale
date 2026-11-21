package com.bluewhale.form.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移脚本基线校验（REQ-SEC-022，form 库）。
 *
 * <p>校验项：版本命名/唯一递增、无 {@code DROP}/{@code TRUNCATE}/{@code DELETE}、
 * {@code V1} 建表集合与 {@code init.sql} 一致并覆盖动态表单元数据模型。
 *
 * @author BlueWhale
 */
class FormFlywayMigrationScriptTest {

    /** 迁移脚本目录（相对模块根目录） */
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    /** 手工初始化脚本（与 V1 基线同源） */
    private static final Path INIT_SCRIPT = Path.of("src/main/resources/db/init.sql");

    /** Flyway 版本化脚本命名 */
    private static final Pattern VERSIONED_NAME = Pattern.compile("V(\\d+)__[A-Za-z0-9_]+.sql");

    /** 建表语句（兼容 init.sql 与 V1 的两种写法） */
    private static final Pattern CREATE_TABLE =
            Pattern.compile("^CREATE TABLE(?: IF NOT EXISTS)? (\\w+) \\(", Pattern.MULTILINE);

    /**
     * 版本命名、唯一递增与破坏性语句校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("迁移脚本命名合规且无破坏性语句")
    void scriptsShouldBeVersionedAndNonDestructive() throws IOException {
        List<Path> scripts = migrationScripts();
        assertFalse(scripts.isEmpty(), "db/migration 目录下应至少存在一个迁移脚本");
        List<Long> versions = scripts.stream().map(FormFlywayMigrationScriptTest::versionOf)
                .sorted().collect(Collectors.toList());
        assertEquals(versions.size(), Set.copyOf(versions).size(), "版本号不可重复：" + versions);
        for (int i = 1; i < versions.size(); i++) {
            // Flyway 按版本号数值排序执行，文件系统字典序不参与；此处按数值校验严格递增
            assertTrue(versions.get(i) > versions.get(i - 1), "脚本版本号必须严格递增：" + versions);
        }
        for (Path script : scripts) {
            for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String statement = line.trim().toUpperCase();
                if (statement.startsWith("--") || statement.isEmpty()) {
                    continue;
                }
                assertFalse(statement.startsWith("DROP "), script.getFileName() + " 含 DROP 语句：" + line);
                assertFalse(statement.startsWith("TRUNCATE "), script.getFileName() + " 含 TRUNCATE 语句：" + line);
                assertFalse(statement.startsWith("DELETE "), script.getFileName() + " 含 DELETE 语句：" + line);
            }
        }
    }

    /**
     * 基线一致性：V1 建表集合与 init.sql 一致且覆盖关键表
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V1 基线表集合一致且覆盖动态表单关键表")
    void baselineShouldMatchInitScript() throws IOException {
        Set<String> initTables = extractTables(INIT_SCRIPT);
        Set<String> baselineTables = extractTables(MIGRATION_DIR.resolve("V1__baseline.sql"));
        assertFalse(initTables.isEmpty(), "init.sql 未解析到建表语句");
        assertEquals(initTables, baselineTables, "V1 基线与 init.sql 的建表集合不一致");
        assertEquals(9, baselineTables.size(), "form 基线应包含 9 张表");
        for (String table : List.of("form_definition", "form_data", "form_table_registry",
                "form_table_definition", "form_column_meta", "form_field_config",
                "form_field_option", "form_definition_version", "api_log")) {
            assertTrue(baselineTables.contains(table), "基线必须包含表：" + table);
        }
        String baseline = Files.readString(MIGRATION_DIR.resolve("V1__baseline.sql"), StandardCharsets.UTF_8);
        for (String column : List.of("table_definition_id", "table_code", "col_span")) {
            assertTrue(baseline.contains(column), "基线应包含元数据模型列：" + column);
        }
    }

    /**
     * 考功殿（T5.7）索引迁移与 init.sql 同步
     *
     * <p>盯两件事：① V16 必须真的建两个复合索引、删两个被最左前缀覆盖的单列索引；
     * ② init.sql 的 {@code form_data} 块必须跟着改——它是空库手工执行的脚本，
     * 若只改 Flyway，空库建出来的表会比存量库少索引，概览在空库上直接全表扫。</p>
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V16 建两个复合索引并清掉被覆盖的单列索引，init.sql 同步")
    void overviewIndexesShouldBeSyncedWithInitScript() throws IOException {
        Path v16 = MIGRATION_DIR.resolve("V16__form_overview_index.sql");
        assertTrue(Files.exists(v16), "缺少 V16 索引迁移脚本");
        String script = Files.readString(v16, StandardCharsets.UTF_8);
        for (String index : List.of("idx_tenant_create", "idx_tenant_biz_update")) {
            assertTrue(script.contains("ADD INDEX " + index), "V16 应补建索引：" + index);
        }
        for (String stale : List.of("idx_tenant_id", "idx_tenant_biz_status")) {
            assertTrue(script.contains("DROP INDEX " + stale),
                    "V16 应删除被复合索引最左前缀覆盖的：" + stale);
        }
        String initSql = Files.readString(INIT_SCRIPT, StandardCharsets.UTF_8);
        int from = initSql.indexOf("CREATE TABLE form_data");
        assertTrue(from > 0, "init.sql 未找到 form_data 建表语句");
        String dataBlock = initSql.substring(from, initSql.indexOf("COMMENT='表单数据表", from));
        for (String token : List.of("idx_tenant_create", "idx_tenant_biz_update",
                "biz_status", "process_instance_id", "client_request_id")) {
            assertTrue(dataBlock.contains(token), "init.sql 的 form_data 应同步：" + token);
        }
        /* 断言前先剥掉注释：块里那段说明文字会提到被删掉的索引名，
           不剥的话"文档写了"会被当成"DDL 建了"。 */
        String ddl = dataBlock.replaceAll("(?s)/\\*.*?\\*/", "");
        assertFalse(ddl.contains("idx_tenant_id"),
                "form_data 的 idx_tenant_id 已被 idx_tenant_create 最左前缀覆盖，init.sql 不应再单建");
        assertFalse(ddl.contains("idx_tenant_biz_status"),
                "form_data 的 idx_tenant_biz_status 已被 idx_tenant_biz_update 覆盖，init.sql 不应再单建");
    }

    /**
     * 列出迁移脚本并按文件名字典序返回
     *
     * @return 迁移脚本路径
     * @throws IOException 读取目录失败
     */
    private static List<Path> migrationScripts() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * 解析脚本版本号
     *
     * @param script 脚本路径
     * @return 版本号
     */
    private static Long versionOf(Path script) {
        Matcher matcher = VERSIONED_NAME.matcher(script.getFileName().toString());
        assertTrue(matcher.matches(), "脚本命名不符合 V<版本>__<描述>.sql：" + script.getFileName());
        return Long.valueOf(matcher.group(1));
    }

    /**
     * 解析脚本中的建表名集合
     *
     * @param path 脚本路径
     * @return 表名集合（保持出现顺序）
     * @throws IOException 读取失败
     */
    private static Set<String> extractTables(Path path) throws IOException {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE.matcher(Files.readString(path, StandardCharsets.UTF_8));
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }
}
