package com.bluewhale.auth.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移脚本基线校验（REQ-SEC-022）：防止危险语句、命名漂移与基线表漏建。
 *
 * <p>校验项：
 * <ol>
 *   <li>脚本命名符合 Flyway 版本化约定（{@code V<版本>__<描述>.sql}）；</li>
 *   <li>脚本不含 {@code DROP} / {@code TRUNCATE} / {@code DELETE} 等破坏性语句；</li>
 *   <li>{@code V1__baseline.sql} 建表集合与 {@code db/init.sql} 一致，避免空库与存量库 schema 漂移；</li>
 *   <li>版本号唯一且严格递增，避免乱序执行。</li>
 * </ol>
 *
 * @author BlueWhale
 */
class FlywayMigrationScriptTest {

    /** 迁移脚本目录（相对模块根目录） */
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    /** 手工初始化脚本（与 V1 基线同源） */
    private static final Path INIT_SCRIPT = Path.of("src/main/resources/db/init.sql");

    /** OA M0-5 Auth 增强手工迁移脚本 */
    private static final Path OA_M0_AUTH_SCRIPT =
            Path.of("src/main/resources/db/migration-5.85-oa-m0-auth.sql");

    /** Flyway 版本化脚本命名 */
    private static final Pattern VERSIONED_NAME = Pattern.compile("V(\\d+)__[A-Za-z0-9_]+.sql");

    /** 建表语句（兼容 init.sql 与 V1 的两种写法） */
    private static final Pattern CREATE_TABLE =
            Pattern.compile("^CREATE TABLE(?: IF NOT EXISTS)? (\\w+) \\(", Pattern.MULTILINE);

    /** DELETE 是否带 WHERE 谓词（无谓词的整表删除一律拒绝，白名单也不豁免） */
    private static final Pattern DELETE_WITH_PREDICATE =
            Pattern.compile("^DELETE\\s+FROM\\s+\\w+\\s+WHERE\\s+\\S", Pattern.CASE_INSENSITIVE);

    /**
     * 已登记的破坏性语句白名单：脚本名 → 理由。
     *
     * <p>Flyway 迁移历史不可变（改脚本会让已迁移库 checksum 校验失败），因此个别
     * 「必须清理历史脏数据」的脚本只能在测试侧登记：登记项仍须带 WHERE 谓词，
     * 且理由必须写清事故背景，避免白名单变成「随便删」的后门。</p>
     */
    private static final Map<String, String> ALLOWED_DELETE_SCRIPTS = Map.of(
            "V7__tenant_config_menu_fix.sql",
            "清理 V6 因 id=37 撞号误复制的 /doc/spaces 菜单；谓词已锁定 id 派生值，不会误伤合法菜单");

    /**
     * 迁移脚本命名与目录完整性校验
     *
     * @throws IOException 读取脚本目录失败
     */
    @Test
    @DisplayName("迁移脚本按 Flyway 版本命名且目录非空")
    void shouldFollowFlywayNaming() throws IOException {
        List<Path> scripts = migrationScripts();
        assertFalse(scripts.isEmpty(), "db/migration 目录下应至少存在一个迁移脚本");
        List<String> versions = new ArrayList<>();
        for (Path script : scripts) {
            String fileName = script.getFileName().toString();
            Matcher matcher = VERSIONED_NAME.matcher(fileName);
            assertTrue(matcher.matches(), "脚本命名不符合 V<版本>__<描述>.sql：" + fileName);
            versions.add(matcher.group(1));
        }
        Set<String> uniqueVersions = new HashSet<>(versions);
        assertEquals(uniqueVersions.size(), versions.size(), "版本号不可重复：" + versions);
        List<Long> ordered = versions.stream().map(Long::valueOf).collect(Collectors.toList());
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(ordered.get(i) > ordered.get(i - 1),
                    "脚本版本号必须严格递增：" + ordered);
        }
    }

    /**
     * 破坏性语句校验：迁移脚本不允许 DROP / TRUNCATE / DELETE
     *
     * <p>例外：{@link #ALLOWED_DELETE_SCRIPTS} 登记过的脚本允许 DELETE，但仍必须带 WHERE 谓词。</p>
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("迁移脚本不含 DROP / TRUNCATE / DELETE（已登记例外须带 WHERE）")
    void shouldNotContainDestructiveStatements() throws IOException {
        for (Path script : migrationScripts()) {
            String fileName = script.getFileName().toString();
            List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                String statement = line.trim().toUpperCase();
                if (statement.startsWith("--") || statement.isEmpty()) {
                    continue;
                }
                assertFalse(statement.startsWith("DROP "),
                        fileName + " 含 DROP 语句：" + line);
                assertFalse(statement.startsWith("TRUNCATE "),
                        fileName + " 含 TRUNCATE 语句：" + line);
                if (statement.startsWith("DELETE ")) {
                    assertTrue(ALLOWED_DELETE_SCRIPTS.containsKey(fileName),
                            fileName + " 含 DELETE 语句且未登记例外：" + line);
                    // DELETE 可能跨多行书写，谓词判定需看整条语句（到分号为止）
                    assertTrue(DELETE_WITH_PREDICATE.matcher(joinStatement(lines, index)).find(),
                            fileName + " 的 DELETE 必须带 WHERE 谓词（禁止整表删除）：" + line);
                }
            }
        }
    }

    /**
     * 白名单条目必须指向真实存在的脚本，避免登记过期后留下「空豁免」
     *
     * @throws IOException 读取脚本目录失败
     */
    @Test
    @DisplayName("破坏性语句白名单条目均指向存在的脚本")
    void allowListEntriesShouldExist() throws IOException {
        Set<String> names = migrationScripts().stream()
                .map(script -> script.getFileName().toString())
                .collect(Collectors.toSet());
        for (Map.Entry<String, String> entry : ALLOWED_DELETE_SCRIPTS.entrySet()) {
            assertTrue(names.contains(entry.getKey()), "白名单脚本不存在：" + entry.getKey());
            assertFalse(entry.getValue().isBlank(), "白名单必须写明理由：" + entry.getKey());
        }
    }

    /**
     * 基线一致性校验：V1 建表集合必须与 init.sql 完全一致
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V1 基线建表集合与 init.sql 一致")
    void baselineTablesShouldMatchInitScript() throws IOException {
        Set<String> initTables = extractTables(INIT_SCRIPT);
        Set<String> baselineTables = extractTables(MIGRATION_DIR.resolve("V1__baseline.sql"));
        assertFalse(initTables.isEmpty(), "init.sql 未解析到建表语句");
        assertEquals(initTables, baselineTables, "V1 基线与 init.sql 的建表集合不一致");
        assertTrue(baselineTables.contains("sys_user"), "基线必须包含 sys_user");
        assertTrue(baselineTables.contains("sys_tenant_password_policy"), "基线必须包含租户口令策略表");
    }

    /**
     * 口令生命周期脚本内容校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("口令生命周期与租户策略脚本包含关键对象")
    void passwordScriptsShouldContainKeyObjects() throws IOException {
        String lifecycle = read(MIGRATION_DIR.resolve("V2__password_lifecycle.sql"));
        assertTrue(lifecycle.contains("password_updated_time"), "V2 应包含最近改密时间列");
        assertTrue(lifecycle.contains("password_notice_time"), "V2 应包含到期提醒时间列");
        assertTrue(lifecycle.contains("sys_password_history"), "V2 应包含口令历史表");

        String policy = read(MIGRATION_DIR.resolve("V3__tenant_password_policy.sql"));
        assertTrue(policy.contains("sys_tenant_password_policy"), "V3 应包含租户口令策略表");
        assertTrue(policy.contains("sys:tenant:pwdpolicy:list"), "V3 应包含租户策略查询权限");
        assertTrue(policy.contains("sys:tenant:pwdpolicy:edit"), "V3 应包含租户策略配置权限");
    }

    /**
     * OA M0-5 Auth 增强迁移校验：包含负责人用户 ID 列与索引，且不含破坏性语句
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("M0-5 Auth 增强迁移包含 leader_user_id 与 idx_tenant_leader 且无破坏性语句")
    void oaM0AuthMigrationShouldContainLeaderUserId() throws IOException {
        assertTrue(Files.exists(OA_M0_AUTH_SCRIPT), "缺少 OA M0-5 Auth 增强迁移脚本");
        String content = read(OA_M0_AUTH_SCRIPT);
        assertTrue(content.contains("leader_user_id"), "迁移应包含 leader_user_id 列");
        assertTrue(content.contains("idx_tenant_leader"), "迁移应包含 idx_tenant_leader 索引");
        assertTrue(content.contains("bluewhale_auth"), "迁移头部应标注适用库 bluewhale_auth");
        for (String line : Files.readAllLines(OA_M0_AUTH_SCRIPT, StandardCharsets.UTF_8)) {
            String statement = line.trim().toUpperCase();
            if (statement.startsWith("--") || statement.isEmpty()) {
                continue;
            }
            assertFalse(statement.startsWith("DROP "), "迁移不得含 DROP 语句：" + line);
            assertFalse(statement.startsWith("TRUNCATE "), "迁移不得含 TRUNCATE 语句：" + line);
            assertFalse(statement.startsWith("DELETE "), "迁移不得含 DELETE 语句：" + line);
        }
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
     * 解析脚本中的建表名集合
     *
     * @param path 脚本路径
     * @return 表名集合（保持出现顺序）
     * @throws IOException 读取失败
     */
    private static Set<String> extractTables(Path path) throws IOException {
        String content = read(path);
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE.matcher(content);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    /**
     * 读取脚本文本（UTF-8）
     *
     * @param path 脚本路径
     * @return 文本内容
     * @throws IOException 读取失败
     */
    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * 从指定行开始拼接一条完整语句（以分号或空行结束），用于多行书写的 DELETE 判谓词
     *
     * @param lines 脚本行
     * @param start 起始行下标
     * @return 单行化的语句文本
     */
    private static String joinStatement(List<String> lines, int start) {
        StringBuilder statement = new StringBuilder();
        for (int index = start; index < lines.size(); index++) {
            String current = lines.get(index).trim();
            if (current.isEmpty()) {
                break;
            }
            statement.append(current).append(' ');
            if (current.endsWith(";")) {
                break;
            }
        }
        return statement.toString();
    }
}
