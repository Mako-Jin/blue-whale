package com.bluewhale.erp.migration;

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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，erp 库）。
 *
 * <p>校验项：版本命名/唯一递增、无 {@code DROP}/{@code TRUNCATE}/{@code DELETE}、
 * {@code V1} 建表集合与 {@code init.sql} 一致。
 *
 * @author BlueWhale
 */
class ErpFlywayMigrationScriptTest {

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
        List<Long> versions = scripts.stream()
                .map(ErpFlywayMigrationScriptTest::versionOf)
                .collect(Collectors.toList());
        assertEquals(versions.size(), Set.copyOf(versions).size(), "版本号不可重复：" + versions);
        for (int i = 1; i < versions.size(); i++) {
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
     * 基线一致性：V1 建表集合与 init.sql 一致
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V1 基线表集合一致")
    void baselineShouldMatchInitScript() throws IOException {
        Set<String> initTables = extractTables(INIT_SCRIPT);
        Set<String> baselineTables = extractTables(MIGRATION_DIR.resolve("V1__baseline.sql"));
        assertFalse(initTables.isEmpty(), "init.sql 未解析到建表语句");
        assertEquals(initTables, baselineTables, "V1 基线与 init.sql 的建表集合不一致");
        assertEquals(4, baselineTables.size(), "ERP 应用 基线应包含 4 张表");
        for (String table : List.of("erp_item", "erp_supplier", "audit_log")) {
            assertTrue(baselineTables.contains(table), "基线必须包含表：" + table);
        }

    }

    /**
     * V2 主数据与财务核心表集合及幂等种子校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V2 建表集合与幂等种子一致")
    void financeCoreMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> financeTables = extractTables(MIGRATION_DIR.resolve("V2__masterdata_finance.sql"));
        assertEquals(Set.of(
                "erp_account", "erp_accounting_period", "erp_customer", "erp_warehouse",
                "erp_uom", "erp_currency", "erp_tax_code",
                "erp_fin_voucher", "erp_fin_voucher_line",
                "erp_fin_receivable", "erp_fin_receivable_settlement",
                "erp_fin_payable", "erp_fin_payable_settlement"), financeTables,
                "V2 应创建 13 张主数据与财务核心表");
        String content = Files.readString(MIGRATION_DIR.resolve("V2__masterdata_finance.sql"),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("INSERT IGNORE INTO erp_account"), "科目种子必须幂等");
        assertTrue(content.contains("INSERT IGNORE INTO erp_accounting_period"), "会计期间种子必须幂等");
        assertTrue(content.contains("INSERT IGNORE INTO erp_customer"), "客户种子必须幂等");
    }

    /**
     * V3 供应链核心表集合与自动记账幂等键校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V3 建表集合与自动记账幂等键一致")
    void scmMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> scmTables = extractTables(MIGRATION_DIR.resolve("V3__scm.sql"));
        assertEquals(Set.of(
                "erp_scm_purchase_order", "erp_scm_purchase_order_line",
                "erp_scm_sales_order", "erp_scm_sales_order_line",
                "erp_scm_stock_movement", "erp_scm_stock_balance"), scmTables,
                "V3 应创建 6 张供应链表");
        String content = Files.readString(MIGRATION_DIR.resolve("V3__scm.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_voucher_source"), "凭证来源唯一键必须存在");
        assertTrue(content.contains("uk_receivable_source"), "应收来源唯一键必须存在");
        assertTrue(content.contains("uk_payable_source"), "应付来源唯一键必须存在");
        assertTrue(content.contains("uk_stock_balance"), "库存余额按物料+仓库唯一");
    }

    /**
     * V4 库存作业表集合与待处理财产损溢科目种子校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V4 库存作业表集合与科目种子一致")
    void stockOpsMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> stockOpsTables = extractTables(MIGRATION_DIR.resolve("V4__scm_stock_ops.sql"));
        assertEquals(Set.of(
                "erp_scm_transfer", "erp_scm_transfer_line",
                "erp_scm_stocktake", "erp_scm_stocktake_line"), stockOpsTables,
                "V4 应创建 4 张库存作业表");
        String content = Files.readString(MIGRATION_DIR.resolve("V4__scm_stock_ops.sql"),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_transfer_doc_no"), "调拨单号唯一键必须存在");
        assertTrue(content.contains("uk_stocktake_doc_no"), "盘点单号唯一键必须存在");
        assertTrue(content.contains("INSERT IGNORE INTO erp_account"), "待处理财产损溢科目种子必须幂等");
        assertTrue(content.contains("'1901'"), "盘盈盘亏科目编码应为 1901");
    }

    /**
     * V5 生产计划表集合与生产成本科目种子校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V5 生产计划表集合与科目种子一致")
    void manufacturingMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> manufacturingTables = extractTables(MIGRATION_DIR.resolve("V5__manufacturing.sql"));
        assertEquals(Set.of(
                "erp_mfg_forecast", "erp_mfg_forecast_line",
                "erp_mfg_mps", "erp_mfg_mps_line",
                "erp_mfg_production_order", "erp_mfg_production_material"), manufacturingTables,
                "V5 应创建 6 张生产计划表");
        String content = Files.readString(MIGRATION_DIR.resolve("V5__manufacturing.sql"),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_forecast_batch"), "预测批次号唯一键必须存在");
        assertTrue(content.contains("uk_mps_plan_no"), "主生产计划单号唯一键必须存在");
        assertTrue(content.contains("uk_mo_order_no"), "生产订单号唯一键必须存在");
        assertTrue(content.contains("INSERT IGNORE INTO erp_account"), "生产成本科目种子必须幂等");
        assertTrue(content.contains("'5001'"), "生产成本科目编码应为 5001");
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