package com.bluewhale.mes.migration;

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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，mes 库）。
 *
 * <p>校验项：版本命名/唯一递增、无 {@code DROP}/{@code TRUNCATE}/{@code DELETE}、
 * {@code V1} 建表集合与 {@code init.sql} 一致、M0 主数据表齐备。
 *
 * @author BlueWhale
 */
class MesFlywayMigrationScriptTest {

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
                .map(MesFlywayMigrationScriptTest::versionOf)
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
        assertEquals(2, baselineTables.size(), "MES 应用 基线应包含 2 张表");
        for (String table : List.of("mes_work_order", "api_log")) {
            assertTrue(baselineTables.contains(table), "基线必须包含表：" + table);
        }

    }

    /**
     * V2 / V3 建表集合：车间主数据、工艺路线快照、发件箱与工作日历齐备
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("M0 车间主数据与骨架表齐备")
    void foundationTablesShouldBePresent() throws IOException {
        Set<String> foundationTables = extractTables(MIGRATION_DIR.resolve("V2__mes_foundation.sql"));
        assertEquals(12, foundationTables.size(), "V2 应创建 12 张表");
        for (String table : List.of(
                "mes_md_workshop",
                "mes_md_line",
                "mes_md_work_center",
                "mes_md_station",
                "mes_md_team",
                "mes_md_shift",
                "mes_md_operation",
                "mes_md_defect_code",
                "mes_md_reason_code",
                "mes_md_routing_snapshot",
                "mes_md_routing_operation",
                "mes_common_outbox")) {
            assertTrue(foundationTables.contains(table), "V2 必须包含表：" + table);
        }

        Set<String> calendarTables = extractTables(MIGRATION_DIR.resolve("V3__mes_shift_calendar.sql"));
        assertEquals(Set.of("mes_md_shift_calendar"), calendarTables, "V3 应仅创建工作日历表");
    }

    /**
     * V4 建表集合：调度派工（5 张）与生产执行（8 张）齐备
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("M1 调度派工与生产执行表齐备")
    void dispatchExecutionTablesShouldBePresent() throws IOException {
        Set<String> dispatchTables = extractTables(MIGRATION_DIR.resolve("V4__mes_dispatch_execution.sql"));
        assertEquals(13, dispatchTables.size(), "V4 应创建 13 张表");
        for (String table : List.of(
                "mes_dp_work_order_task",
                "mes_dp_dispatch_order",
                "mes_dp_dispatch_line",
                "mes_dp_schedule_plan",
                "mes_dp_schedule_slot",
                "mes_ex_production_report",
                "mes_ex_report_defect",
                "mes_ex_material_issue",
                "mes_ex_material_issue_line",
                "mes_ex_material_return",
                "mes_ex_wip_snapshot",
                "mes_ex_exception",
                "mes_ex_exception_action")) {
            assertTrue(dispatchTables.contains(table), "V4 必须包含表：" + table);
        }
    }

    /**
     * V5 建表集合：物料与追溯 8 张表齐备，空库累计 36 张业务表
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("M2 物料与追溯表齐备")
    void traceTablesShouldBePresent() throws IOException {
        Set<String> traceTables = extractTables(MIGRATION_DIR.resolve("V5__mes_trace.sql"));
        assertEquals(8, traceTables.size(), "V5 应创建 8 张表");
        for (String table : List.of(
                "mes_tr_batch",
                "mes_tr_barcode",
                "mes_tr_barcode_rule",
                "mes_tr_trace_link",
                "mes_tr_line_side_stock",
                "mes_tr_line_side_movement",
                "mes_tr_scan_rule",
                "mes_tr_replenishment")) {
            assertTrue(traceTables.contains(table), "V5 必须包含表：" + table);
        }
        int total = 0;
        for (Path script : migrationScripts()) {
            total += extractTables(script).size();
        }
        assertEquals(69, total,
                "空库累计应为 69 张业务表（V1 2 + V2 12 + V3 1 + V4 13 + V5 8 + V6 10 + V7 15 + V8 6 + V9 1 + V10 0 + V11 1）");
    }

    /**
     * V6 建表集合：车间质量 10 张表齐备
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("M3 车间质量表齐备")
    void qualityTablesShouldBePresent() throws IOException {
        Set<String> qualityTables = extractTables(MIGRATION_DIR.resolve("V6__mes_quality.sql"));
        assertEquals(10, qualityTables.size(), "V6 应创建 10 张表");
        for (String table : List.of(
                "mes_qc_inspection_plan",
                "mes_qc_inspection_plan_item",
                "mes_qc_inspection_task",
                "mes_qc_inspection_result",
                "mes_qc_inspection_defect",
                "mes_qc_ncr",
                "mes_qc_disposition",
                "mes_qc_rework_order",
                "mes_qc_spc_sample",
                "mes_qc_spc_rule")) {
            assertTrue(qualityTables.contains(table), "V6 必须包含表：" + table);
        }
    }

    /**
     * V7 建表集合：设备与数据采集 15 张表齐备
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("M4 设备与数据采集表齐备")
    void equipmentCollectTablesShouldBePresent() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V7__mes_equipment_collect.sql"));
        assertEquals(15, tables.size(), "V7 应创建 15 张表");
        for (String table : List.of(
                "mes_eq_equipment",
                "mes_eq_status_event",
                "mes_eq_downtime",
                "mes_eq_maintenance_plan",
                "mes_eq_maintenance_order",
                "mes_eq_repair_order",
                "mes_eq_spare_part",
                "mes_eq_spare_part_stock",
                "mes_eq_spare_part_movement",
                "mes_eq_oee_snapshot",
                "mes_cl_device",
                "mes_cl_collection_point",
                "mes_cl_metric_sample",
                "mes_cl_board_config",
                "mes_cl_archive_policy")) {
            assertTrue(tables.contains(table), "V7 必须包含表：" + table);
        }
    }

    /**
     * V8 建表集合：人员绩效 6 张表齐备
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("M5 人员绩效表齐备")
    void workforceTablesShouldBePresent() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V8__mes_workforce.sql"));
        assertEquals(6, tables.size(), "V8 应创建 6 张表");
        for (String table : List.of(
                "mes_wf_team_member",
                "mes_wf_shift_assignment",
                "mes_wf_handover",
                "mes_wf_work_hour",
                "mes_wf_performance_stat",
                "mes_wf_skill_matrix")) {
            assertTrue(tables.contains(table), "V8 必须包含表：" + table);
        }
    }

    /**
     * 列出迁移脚本并按版本号数值升序返回（文件名字典序会把 V10 排在 V2 之前）
     *
     * @return 迁移脚本路径
     * @throws IOException 读取目录失败
     */
    private static List<Path> migrationScripts() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingLong(MesFlywayMigrationScriptTest::versionOf))
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