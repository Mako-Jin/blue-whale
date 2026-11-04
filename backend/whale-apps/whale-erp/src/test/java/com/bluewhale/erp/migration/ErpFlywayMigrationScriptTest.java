package com.bluewhale.erp.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
     * V6 人力表集合、薪资期间唯一键与应付职工薪酬科目种子校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V6 人力表集合与科目种子一致")
    void hrMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> hrTables = extractTables(MIGRATION_DIR.resolve("V6__hr.sql"));
        assertEquals(Set.of(
                "erp_hr_org_unit", "erp_hr_employee", "erp_hr_employment_change",
                "erp_hr_attendance_summary", "erp_hr_payroll_run", "erp_hr_payslip",
                "erp_hr_performance_cycle", "erp_hr_kpi", "erp_hr_appraisal"), hrTables,
                "V6 应创建 9 张人力表");
        String content = Files.readString(MIGRATION_DIR.resolve("V6__hr.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_hr_org_code"), "组织编码唯一键必须存在");
        assertTrue(content.contains("uk_hr_employee_no"), "员工编号唯一键必须存在");
        assertTrue(content.contains("uk_payroll_period"), "薪资期间唯一键必须存在");
        assertTrue(content.contains("uk_payslip_employee"), "工资条按批次+员工唯一");
        assertTrue(content.contains("uk_hr_appraisal"), "考核结果按周期+员工+指标唯一");
        assertTrue(content.contains("INSERT IGNORE INTO erp_account"), "应付职工薪酬科目种子必须幂等");
        assertTrue(content.contains("'2241'"), "其他应付款科目编码应为 2241");
    }

    /**
     * V7 质量与设备表集合、OEE 幂等键与量具编号唯一键校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V7 质量设备表集合与关键唯一键一致")
    void qcMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> qcTables = extractTables(MIGRATION_DIR.resolve("V7__qc.sql"));
        assertEquals(Set.of(
                "erp_qc_inspection_order", "erp_qc_inspection_item", "erp_qc_ncr",
                "erp_qc_quality_cost", "erp_qc_equipment", "erp_qc_maintenance_plan",
                "erp_qc_maintenance_order", "erp_qc_oee_record", "erp_qc_measuring_tool",
                "erp_qc_calibration_plan", "erp_qc_calibration_record"), qcTables,
                "V7 应创建 11 张质量与设备表");
        String content = Files.readString(MIGRATION_DIR.resolve("V7__qc.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_qc_order_no"), "检验单号唯一键必须存在");
        assertTrue(content.contains("uk_qc_item_line"), "检验项按检验单+行号唯一");
        assertTrue(content.contains("uk_qc_ncr_no"), "不合格品单号唯一键必须存在");
        assertTrue(content.contains("uk_qc_equipment_no"), "设备编码唯一键必须存在");
        assertTrue(content.contains("uk_oee_equipment_date"), "OEE 按设备+日期唯一，保证回传幂等");
        assertTrue(content.contains("uk_qc_tool_no"), "量具编号唯一键必须存在");
    }

    /**
     * V8 分析与报表表集合、预警去重唯一键与只读事实视图校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V8 分析报表表集合与预警去重唯一键一致")
    void analyticsMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> analyticsTables = extractTables(MIGRATION_DIR.resolve("V8__bi.sql"));
        assertEquals(Set.of(
                "erp_bi_cockpit_snapshot", "erp_bi_report_def", "erp_bi_report_schedule",
                "erp_bi_alert_rule", "erp_bi_alert_event"), analyticsTables,
                "V8 应创建 5 张分析与报表表");
        String content = Files.readString(MIGRATION_DIR.resolve("V8__bi.sql"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_bi_cockpit_period_date"), "驾驶舱快照按期间+统计日唯一");
        assertTrue(content.contains("uk_bi_report_no"), "报表编号唯一键必须存在");
        assertTrue(content.contains("uk_bi_schedule_no"), "报表计划编号唯一键必须存在");
        assertTrue(content.contains("uk_bi_alert_rule_no"), "预警规则编号唯一键必须存在");
        assertTrue(content.contains("uk_bi_alert_event_scope"),
                "预警事件按规则+对象+周期唯一，保证不重复触发");
        assertTrue(content.contains("CREATE OR REPLACE VIEW v_erp_bi_sales_fact"),
                "销售事实只读视图必须存在");
        assertTrue(content.contains("CREATE OR REPLACE VIEW v_erp_bi_stock_fact"),
                "库存事实只读视图必须存在");
        assertTrue(content.contains("CREATE OR REPLACE VIEW v_erp_bi_receivable_fact"),
                "应收事实只读视图必须存在");
    }

    /**
     * V9 退货表集合与单号/分录唯一键校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V9 退货表集合与关键唯一键一致")
    void returnsMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> returnTables = extractTables(MIGRATION_DIR.resolve("V9__scm_returns.sql"));
        assertEquals(Set.of(
                "erp_scm_purchase_return", "erp_scm_purchase_return_line",
                "erp_scm_sales_return", "erp_scm_sales_return_line"), returnTables,
                "V9 应创建 4 张退货表");
        String content = Files.readString(MIGRATION_DIR.resolve("V9__scm_returns.sql"),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_pr_doc_no"), "采购退货单号唯一键必须存在");
        assertTrue(content.contains("uk_sr_doc_no"), "销售退货单号唯一键必须存在");
        assertTrue(content.contains("uk_pr_line"), "采购退货分录按单+行号唯一");
        assertTrue(content.contains("uk_sr_line"), "销售退货分录按单+行号唯一");
    }

    /**
     * V10 采购申请与销售报价表集合及单号/分录唯一键校验
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V10 申请报价表集合与关键唯一键一致")
    void requisitionQuotationMigrationShouldCreateExpectedTables() throws IOException {
        Set<String> documentTables = extractTables(
                MIGRATION_DIR.resolve("V10__scm_requisition_quotation.sql"));
        assertEquals(Set.of(
                "erp_scm_purchase_requisition", "erp_scm_purchase_requisition_line",
                "erp_scm_sales_quotation", "erp_scm_sales_quotation_line"), documentTables,
                "V10 应创建 4 张申请与报价表");
        String content = Files.readString(MIGRATION_DIR.resolve("V10__scm_requisition_quotation.sql"),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("uk_pa_doc_no"), "采购申请单号唯一键必须存在");
        assertTrue(content.contains("uk_pa_line"), "采购申请分录按单+行号唯一");
        assertTrue(content.contains("uk_sq_doc_no"), "销售报价单号唯一键必须存在");
        assertTrue(content.contains("uk_sq_line"), "销售报价分录按单+行号唯一");
        assertFalse(content.toUpperCase().contains("FOREIGN KEY"), "申请与报价表不得建立物理外键");
    }

    /**
     * 列出迁移脚本并按版本号升序返回
     *
     * @return 迁移脚本路径
     * @throws IOException 读取目录失败
     */
    private static List<Path> migrationScripts() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingLong(ErpFlywayMigrationScriptTest::versionOf))
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