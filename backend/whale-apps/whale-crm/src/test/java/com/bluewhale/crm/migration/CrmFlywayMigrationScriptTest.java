package com.bluewhale.crm.migration;

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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，crm 库）。
 *
 * <p>门禁通过前只允许建表与索引，禁止种子业务数据；脚本必须可重复执行。</p>
 *
 * @author BlueWhale
 */
class CrmFlywayMigrationScriptTest {

    /** 迁移脚本目录 */
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    /** 建表语句 */
    private static final Pattern CREATE_TABLE =
            Pattern.compile("^CREATE TABLE(?: IF NOT EXISTS)? (\\w+) \\(", Pattern.MULTILINE);

    @Test
    @DisplayName("基线脚本无破坏性语句")
    void scriptsShouldBeNonDestructive() throws IOException {
        List<Path> scripts = migrationScripts();
        assertFalse(scripts.isEmpty(), "db/migration 目录下应至少存在一个迁移脚本");
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

    @Test
    @DisplayName("V1 基线包含 CRM 模型与审计表，且无业务种子数据")
    void baselineShouldContainCrmTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V1__baseline.sql"));
        assertEquals(Set.of("crm_customer", "crm_contact", "crm_opportunity", "crm_follow_up",
                "audit_log", "api_log"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V1__baseline.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "门禁通过前不得写入客户 / 联系人 / 商机 / 跟进种子数据");
    }

    @Test
    @DisplayName("V2 底座：新增集成四表，且不写业务种子数据")
    void foundationShouldContainIntegrationTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V2__crm_foundation.sql"));
        assertEquals(Set.of("crm_common_outbox", "crm_int_sync_log", "crm_external_ref", "crm_idempotency"),
                tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V2__crm_foundation.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "底座增量不得写入客户 / 联系人 / 商机种子数据");
    }

    @Test
    @DisplayName("V2 通过 information_schema 守卫增列，不依赖 MySQL 不支持的 ADD COLUMN IF NOT EXISTS")
    void foundationShouldGuardColumnAdds() throws IOException {
        String content = Files.readString(MIGRATION_DIR.resolve("V2__crm_foundation.sql"), StandardCharsets.UTF_8);
        String upper = content.toUpperCase();
        assertTrue(upper.contains("INFORMATION_SCHEMA.COLUMNS"), "增列必须用 information_schema 存在性守卫");
        assertTrue(upper.contains("PREPARE STMT FROM @DDL"), "守卫后应通过动态 SQL 执行增列");
        boolean forbiddenSyntax = Files.readAllLines(MIGRATION_DIR.resolve("V2__crm_foundation.sql"), StandardCharsets.UTF_8)
                .stream()
                .map(line -> line.trim().toUpperCase())
                .filter(line -> !line.startsWith("--"))
                .anyMatch(line -> line.contains("ADD COLUMN IF NOT EXISTS"));
        assertFalse(forbiddenSyntax, "MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS（注释除外）");
        assertTrue(upper.contains("ADD COLUMN POOL_STATUS"), "公海状态列应由底座增量补齐");
    }

    @Test
    @DisplayName("V3 画像：新增地址 / 画像 / 公海规则 / 负责人留痕四表")
    void profileShouldContainCustomerTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V3__customer_profile.sql"));
        assertEquals(Set.of("crm_customer_address", "crm_customer_profile",
                "crm_customer_pool_rule", "crm_customer_owner_log"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V3__customer_profile.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "画像增量不得写入客户 / 联系人种子数据");
        assertTrue(content.contains("UNIQUE KEY uk_crm_profile_customer"),
                "客户画像每租户每客户唯一");
    }

    @Test
    @DisplayName("V4 销售过程：新增线索 / 线索活动 / 商机阶段历史三表，且不写种子数据")
    void salesShouldContainSalesTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V4__sales_process.sql"));
        assertEquals(Set.of("crm_lead", "crm_lead_activity", "crm_opportunity_stage_history"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V4__sales_process.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "销售过程增量不得写入线索 / 商机种子数据");
        assertTrue(content.toUpperCase().contains("INFORMATION_SCHEMA.COLUMNS"),
                "商机补列必须用 information_schema 存在性守卫");
        assertTrue(content.toUpperCase().contains("ADD COLUMN PROBABILITY"),
                "商机增量应补齐赢单概率列");
        assertTrue(content.contains("UNIQUE KEY uk_crm_lead_no"), "线索编号租户内唯一");
    }

    @Test
    @DisplayName("V5 报价：新增报价头 / 行 / 审批三表，且不写种子数据")
    void quoteShouldContainQuoteTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V5__quote.sql"));
        assertEquals(Set.of("crm_quote", "crm_quote_line", "crm_quote_approval"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V5__quote.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "报价增量不得写入报价 / 报价行种子数据");
        assertTrue(content.contains("UNIQUE KEY uk_crm_quote_no_version"),
                "同编号多版本必须按租户 + 编号 + 版本唯一");
        assertTrue(content.contains("UNIQUE KEY uk_crm_quote_approval_instance"),
                "审批实例在租户内唯一");
    }

    @Test
    @DisplayName("V6 预测与目标：新增快照 / 预测调整 / 目标 / 目标调整四表，且不写种子数据")
    void forecastTargetShouldContainTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V6__forecast_target.sql"));
        assertEquals(Set.of("crm_forecast_snapshot", "crm_forecast_adjustment",
                "crm_sales_target", "crm_sales_target_adjustment"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V6__forecast_target.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "预测与目标增量不得写入快照 / 目标种子数据");
        assertTrue(content.contains("UNIQUE KEY uk_crm_forecast_period_scope"),
                "同周期同范围的预测快照必须唯一");
        assertTrue(content.contains("UNIQUE KEY uk_crm_sales_target_period_scope"),
                "同周期同范围的销售目标必须唯一");
    }

    @Test
    @DisplayName("V7 商务：新增合同台账 / 回款计划 / 催收记录三表，且不写种子数据")
    void commerceShouldContainTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V7__commerce.sql"));
        assertEquals(Set.of("crm_contract", "crm_collection_plan", "crm_collection_action"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V7__commerce.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "商务增量不得写入合同 / 回款种子数据");
        assertTrue(content.contains("UNIQUE KEY uk_crm_contract_source"),
                "同一合同在租户内只允许一条台账投影");
        assertTrue(content.contains("KEY idx_crm_collection_date"),
                "回款计划需按计划日期与状态检索");
    }

    @Test
    @DisplayName("V8 商务订单：新增订单头 / 订单行 / 下发日志三表，且不写种子数据")
    void commerceOrderShouldContainTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V8__commerce_order.sql"));
        assertEquals(Set.of("crm_sales_order", "crm_sales_order_line", "crm_order_push_log"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V8__commerce_order.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "商务订单增量不得写入订单种子数据");
        assertTrue(content.contains("UNIQUE KEY uk_crm_sales_order_no"),
                "订单编号在租户内唯一");
        assertTrue(content.contains("UNIQUE KEY uk_crm_sales_order_source"),
                "同一来源单据只允许转换一次商务订单");
    }

    @Test
    @DisplayName("V9 发票与应收：新增开票申请 / 申请行 / 应收快照三表，且不写种子数据")
    void invoiceShouldContainTables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V9__invoice.sql"));
        assertEquals(Set.of("crm_invoice_request", "crm_invoice_item", "crm_ar_snapshot"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V9__invoice.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "发票与应收增量不得写入开票申请种子数据");
        assertTrue(content.contains("UNIQUE KEY uk_crm_invoice_request_no"),
                "开票申请单号在租户内唯一");
        assertTrue(content.contains("UNIQUE KEY uk_crm_ar_snapshot_day"),
                "同一客户同一天同币种只允许一条应收快照");
    }

    @Test
    @DisplayName("V10 售后服务：一次建齐工单 / SLA / 知识引用 / 满意度 / 退换货九表，且不写种子数据")
    void serviceShouldContainAllM4Tables() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V10__service.sql"));
        assertEquals(Set.of("crm_ticket", "crm_ticket_action", "crm_ticket_sla", "crm_service_calendar",
                "crm_kb_ref", "crm_satisfaction_survey", "crm_satisfaction_response",
                "crm_return_request", "crm_return_item"), tables);
        String content = Files.readString(MIGRATION_DIR.resolve("V10__service.sql"), StandardCharsets.UTF_8);
        assertFalse(content.toUpperCase().contains("INSERT INTO CRM_"),
                "售后服务增量不得写入工单 / 知识库种子数据");
        assertTrue(content.contains("UNIQUE KEY uk_crm_ticket_no"), "工单号在租户内唯一");
        assertTrue(content.contains("UNIQUE KEY uk_crm_ticket_sla_priority"), "同一优先级只允许一条 SLA 策略");
        assertTrue(content.contains("UNIQUE KEY uk_crm_survey_token"), "满意度令牌哈希在租户内唯一");
        assertTrue(content.contains("UNIQUE KEY uk_crm_return_item_no"), "退换货申请行号在租户内唯一");
    }

    /**
     * 列出迁移脚本。
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
     * 解析脚本中的建表名集合。
     *
     * @param path 脚本路径
     * @return 表名集合
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
