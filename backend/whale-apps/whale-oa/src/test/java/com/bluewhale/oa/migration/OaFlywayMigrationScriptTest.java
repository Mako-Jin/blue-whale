package com.bluewhale.oa.migration;

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
 * Flyway 迁移脚本基线校验（REQ-SEC-022，oa 库）。
 *
 * <p>校验项：版本命名/唯一递增、无 {@code DROP}/{@code TRUNCATE}/{@code DELETE}、
 * {@code V1} 建表集合与 {@code init.sql} 一致。
 *
 * @author BlueWhale
 */
class OaFlywayMigrationScriptTest {

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
                .map(OaFlywayMigrationScriptTest::versionOf)
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
        assertEquals(2, baselineTables.size(), "OA 应用 基线应包含 2 张表");
        for (String table : List.of("oa_leave", "api_log")) {
            assertTrue(baselineTables.contains(table), "基线必须包含表：" + table);
        }

    }

    /**
     * 审批底座表集合：与 Wave 5.82 M0 设计稿一致
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V2 审批底座表集合完整")
    void approvalFoundationShouldMatchDesign() throws IOException {
        Set<String> tables = extractTables(MIGRATION_DIR.resolve("V2__approval_foundation.sql"));
        assertEquals(Set.of(
                "oa_approval_config",
                "oa_approval_instance",
                "oa_approval_route_rule",
                "oa_approval_route",
                "oa_approval_effect",
                "oa_file_ref",
                "oa_sequence",
                "oa_integration_outbox"), tables, "V2 建表集合与 M0 设计不一致");
    }

    /**
     * V3 种子请假审批配置与两条审批人规则，且脚本无破坏性语句
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V3 种子请假审批配置与审批人规则")
    void v3ShouldSeedLeaveConfigAndRouteRules() throws IOException {
        Path v3 = MIGRATION_DIR.resolve("V3__oa_m0_seed.sql");
        String script = Files.readString(v3, StandardCharsets.UTF_8);
        assertTrue(script.contains("oa_approval_config"), "V3 应种子审批业务配置");
        assertTrue(script.contains("'LEAVE'"), "V3 应种子 LEAVE 业务类型");
        assertTrue(script.contains("'OA_LEAVE'"), "V3 应绑定 OA_LEAVE 流程定义");
        assertTrue(script.contains("oa_approval_route_rule"), "V3 应种子审批人解析规则");
        assertTrue(script.contains("DEPT_LEADER"), "V3 应种子部门负责人解析器");
        assertTrue(script.contains("'oa_hr_admin'"), "V3 应种子 HR 角色解析器");
        for (String line : Files.readAllLines(v3, StandardCharsets.UTF_8)) {
            String statement = line.trim().toUpperCase();
            if (statement.startsWith("--") || statement.isEmpty()) {
                continue;
            }
            assertFalse(statement.startsWith("DROP "), "V3 含 DROP 语句：" + line);
            assertFalse(statement.startsWith("TRUNCATE "), "V3 含 TRUNCATE 语句：" + line);
            assertFalse(statement.startsWith("DELETE "), "V3 含 DELETE 语句：" + line);
        }
    }

    /**
     * V4 新建请假闭环 5 张表，并为 oa_leave 幂等扩展 6 列
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V4 请假闭环表集合与 oa_leave 扩展列完整")
    void v4ShouldCreateLeaveTablesAndExtendLeave() throws IOException {
        Path v4 = MIGRATION_DIR.resolve("V4__oa_m1a_leave.sql");
        Set<String> tables = extractTables(v4);
        assertEquals(Set.of(
                "oa_employee_profile",
                "oa_leave_type",
                "oa_leave_balance",
                "oa_leave_balance_log",
                "oa_work_calendar"), tables, "V4 建表集合与 M1A 设计不一致");

        String script = Files.readString(v4, StandardCharsets.UTF_8);
        for (String column : List.of("applicant_id", "leave_type_id", "start_period",
                "end_period", "handover_user_id", "approval_instance_id")) {
            assertTrue(script.contains("COLUMN_NAME = '" + column + "'"),
                    "V4 应通过 information_schema 守卫扩展列：" + column);
            assertTrue(script.contains("ALTER TABLE oa_leave ADD COLUMN " + column),
                    "V4 应包含 oa_leave 扩展列 DDL：" + column);
        }
        for (String code : List.of("'ANNUAL'", "'SICK'", "'PERSONAL'")) {
            assertTrue(script.contains(code), "V4 应种子假期类型：" + code);
        }
    }

    /**
     * V5 新建加班申请表，并种子 OVERTIME 审批配置与调休假期类型
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V5 加班申请表与审批/调休种子完整")
    void v5ShouldCreateOvertimeTableAndSeedConfigs() throws IOException {
        Path v5 = MIGRATION_DIR.resolve("V5__oa_m1b_overtime.sql");
        Set<String> tables = extractTables(v5);
        assertEquals(Set.of("oa_overtime"), tables, "V5 建表集合与 M1B 设计不一致");

        String script = Files.readString(v5, StandardCharsets.UTF_8);
        for (String column : List.of("tenant_id", "code", "applicant", "overtime_date", "start_time",
                "end_time", "hours", "compensation", "status", "approval_instance_id")) {
            assertTrue(script.contains(column), "V5 oa_overtime 应包含列：" + column);
        }
        assertTrue(script.contains("'OVERTIME'"), "V5 应种子 OVERTIME 业务类型");
        assertTrue(script.contains("'OA_OVERTIME'"), "V5 应绑定 OA_OVERTIME 流程定义");
        assertTrue(script.contains("DEPT_LEADER"), "V5 应种子部门负责人审批规则");
        assertTrue(script.contains("'COMP_TIME'"), "V5 应种子调休假期类型");
        assertTrue(script.contains("uk_oa_overtime_code"), "V5 应建立单号唯一索引");
    }

    /**
     * V6 新建出差申请、行程与预支表，并种子 TRIP 审批配置
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V6 出差表集合与审批种子完整")
    void v6ShouldCreateTripTablesAndSeedConfigs() throws IOException {
        Path v6 = MIGRATION_DIR.resolve("V6__oa_m1b_trip.sql");
        Set<String> tables = extractTables(v6);
        assertEquals(Set.of("oa_trip", "oa_trip_itinerary", "oa_trip_advance"), tables,
                "V6 建表集合与 M1B-2 设计不一致");

        String script = Files.readString(v6, StandardCharsets.UTF_8);
        for (String column : List.of("tenant_id", "code", "applicant", "destination", "start_date",
                "end_date", "days", "transport_type", "budget_amount", "advance_amount",
                "report_status", "report_content", "report_time", "status", "approval_instance_id")) {
            assertTrue(script.contains(column), "V6 oa_trip 应包含列：" + column);
        }
        assertTrue(script.contains("'TRIP'"), "V6 应种子 TRIP 业务类型");
        assertTrue(script.contains("'OA_TRIP'"), "V6 应绑定 OA_TRIP 流程定义");
        assertTrue(script.contains("DEPT_LEADER"), "V6 应种子部门负责人审批规则");
        assertTrue(script.contains("uk_oa_trip_code"), "V6 应建立单号唯一索引");
        assertTrue(script.contains("uk_oa_trip_advance_trip"), "V6 应保证每单最多一条预支记录");
    }

    /**
     * V7 新建考勤 10 张表，并种子默认班次/规则与补卡审批配置
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V7 考勤表集合与审批种子完整")
    void v7ShouldCreateAttendanceTablesAndSeedConfigs() throws IOException {
        Path v7 = MIGRATION_DIR.resolve("V7__attendance.sql");
        Set<String> tables = extractTables(v7);
        assertEquals(Set.of("oa_att_shift", "oa_att_rule", "oa_att_location", "oa_att_device",
                "oa_att_group", "oa_att_group_member", "oa_att_record", "oa_att_daily",
                "oa_att_exception", "oa_att_correction"), tables, "V7 建表集合与 M2-5a 设计不一致");

        String script = Files.readString(v7, StandardCharsets.UTF_8);
        for (String column : List.of("work_start", "work_end", "flex_minutes", "max_corrections_per_month",
                "punch_time", "punch_type", "work_date", "late_minutes", "early_minutes", "approval_instance_id")) {
            assertTrue(script.contains(column), "V7 考勤表应包含列：" + column);
        }
        assertTrue(script.contains("'ATT_CORRECTION'"), "V7 应种子补卡业务类型");
        assertTrue(script.contains("'OA_ATT_CORRECTION'"), "V7 应绑定补卡流程定义");
        assertTrue(script.contains("DEPT_LEADER"), "V7 应种子部门负责人审批规则");
        assertTrue(script.contains("uk_oa_att_record_punch"), "V7 应保证打卡幂等");
        assertTrue(script.contains("uk_oa_att_daily_user_date"), "V7 应保证日汇总唯一");
    }

    /**
     * V8 新建报销 5 张表与行政 8 张表，并种子费用类别/标准与三类行政审批配置
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V8 报销与行政表集合及审批种子完整")
    void v8ShouldCreateExpenseAdminTablesAndSeedConfigs() throws IOException {
        Path v8 = MIGRATION_DIR.resolve("V8__expense_admin.sql");
        Set<String> tables = extractTables(v8);
        assertEquals(Set.of("oa_expense", "oa_expense_item", "oa_expense_invoice",
                "oa_expense_category", "oa_expense_standard",
                "oa_vehicle", "oa_driver", "oa_vehicle_booking", "oa_vehicle_usage",
                "oa_seal_use", "oa_seal_use_file", "oa_purchase_request", "oa_purchase_item"),
                tables, "V8 建表集合与 M2-5b/5c 设计不一致");

        String script = Files.readString(v8, StandardCharsets.UTF_8);
        for (String column : List.of("code", "applicant", "total_amount", "currency", "status",
                "approval_instance_id", "erp_push_status", "erp_payable_no", "line_no",
                "invoice_no", "level_code", "limit_amount",
                "plate_no", "current_mileage", "start_time", "end_time", "seal_id",
                "registered_time", "item_code", "unit_price", "supplier_advice", "erp_po_no")) {
            assertTrue(script.contains(column), "V8 表应包含列：" + column);
        }
        for (String code : List.of("'TRAVEL'", "'MEAL'", "'OFFICE'", "'OTHER'")) {
            assertTrue(script.contains(code), "V8 应种子费用类别：" + code);
        }
        for (String type : List.of("'EXPENSE'", "'VEHICLE'", "'SEAL_USE'", "'PURCHASE'")) {
            assertTrue(script.contains(type), "V8 应种子业务类型：" + type);
        }
        for (String definition : List.of("'OA_EXPENSE'", "'OA_VEHICLE'", "'OA_SEAL'",
                "'OA_PURCHASE'")) {
            assertTrue(script.contains(definition), "V8 应绑定流程定义：" + definition);
        }
        assertTrue(script.contains("DEPT_LEADER"), "V8 应种子部门负责人审批规则");
        assertTrue(script.contains("'oa_finance'"), "V8 应种子财务角色审批规则");
        assertTrue(script.contains("uk_oa_expense_code"), "V8 应建立报销单号唯一索引");
        assertTrue(script.contains("uk_oa_expense_invoice_no"), "V8 应建立发票查重唯一索引");
        assertTrue(script.contains("uk_oa_vehicle_plate"), "V8 应建立车牌唯一索引");
        assertTrue(script.contains("uk_oa_vehicle_booking_code"), "V8 应建立用车单号唯一索引");
        assertTrue(script.contains("uk_oa_vehicle_usage_booking"), "V8 应用车登记幂等");
        assertTrue(script.contains("uk_oa_seal_use_code"), "V8 应建立用章单号唯一索引");
        assertTrue(script.contains("uk_oa_purchase_request_code"), "V8 应建立采购单号唯一索引");
        assertTrue(script.contains("uk_oa_purchase_item_line"), "V8 应保证采购明细行号唯一");
    }

    /**
     * V9 新建协同办公 9 张表，并保证关键唯一索引
     *
     * @throws IOException 读取脚本失败
     */
    @Test
    @DisplayName("V9 协同办公表集合与索引完整")
    void v9ShouldCreateCollabTables() throws IOException {
        Path v9 = MIGRATION_DIR.resolve("V9__collab.sql");
        Set<String> tables = extractTables(v9);
        assertEquals(Set.of("oa_meeting_room", "oa_meeting", "oa_meeting_participant",
                "oa_meeting_minute", "oa_meeting_action", "oa_notice", "oa_notice_recipient",
                "oa_task", "oa_task_comment"), tables, "V9 建表集合与 M3-P1 设计不一致");

        String script = Files.readString(v9, StandardCharsets.UTF_8);
        for (String column : List.of("tenant_id", "name", "capacity", "start_time", "end_time",
                "participant", "read_flag", "read_time", "priority", "due_date", "source_id",
                "finish_time", "task_id", "content")) {
            assertTrue(script.contains(column), "V9 协同表应包含列：" + column);
        }
        assertTrue(script.contains("uk_oa_meeting_room_name"), "V9 应保证会议室名称租户唯一");
        assertTrue(script.contains("uk_oa_meeting_code"), "V9 应保证会议单号租户唯一");
        assertTrue(script.contains("uk_oa_meeting_participant_user"), "V9 应保证参会人唯一");
        assertTrue(script.contains("uk_oa_meeting_minute_meeting"), "V9 应保证每场会议一份纪要");
        assertTrue(script.contains("uk_oa_notice_recipient_user"), "V9 应保证公告接收人唯一");
    }

    @Test
    @DisplayName("V10 创建日程与日程共享表")
    void v10ShouldCreateCalendarTables() throws IOException {
        Path v10 = MIGRATION_DIR.resolve("V10__calendar.sql");
        Set<String> tables = extractTables(v10);
        assertEquals(Set.of("oa_calendar", "oa_calendar_share"), tables, "V10 建表集合与 M3-P2 日程设计不一致");

        String script = Files.readString(v10, StandardCharsets.UTF_8);
        for (String column : List.of("tenant_id", "owner_id", "owner_name", "title", "start_time",
                "end_time", "all_day", "visibility", "remind_minutes", "source_id",
                "calendar_id", "user_id", "user_name")) {
            assertTrue(script.contains(column), "V10 日程表应包含列：" + column);
        }
        assertTrue(script.contains("uk_oa_calendar_share_user"), "V10 应保证同一日程对同一用户仅共享一次");
        assertTrue(script.contains("idx_oa_calendar_owner_time"), "V10 应按所有者与时间建索引");
    }

    @Test
    @DisplayName("V11 创建网盘文件夹、文件登记与授权表")
    void v11ShouldCreateDriveTables() throws IOException {
        Path v11 = MIGRATION_DIR.resolve("V11__drive.sql");
        Set<String> tables = extractTables(v11);
        assertEquals(Set.of("oa_drive_folder", "oa_drive_file", "oa_drive_acl"), tables,
                "V11 建表集合与 M3-P2 网盘设计不一致");

        String script = Files.readString(v11, StandardCharsets.UTF_8);
        for (String column : List.of("parent_id", "scope", "dept_id", "owner_id", "sort_no",
                "folder_id", "file_id", "file_name", "file_size", "content_type", "uploader_id",
                "subject_type", "subject_id", "effect", "permission")) {
            assertTrue(script.contains(column), "V11 网盘表应包含列：" + column);
        }
        assertTrue(script.contains("uk_oa_drive_file_folder"), "V11 应保证同一文件夹内文件登记唯一");
        assertTrue(script.contains("uk_oa_drive_acl_subject"), "V11 应保证同一主体授权唯一");
        assertTrue(script.contains("idx_oa_drive_folder_parent"), "V11 应按父目录建索引");
    }

    @Test
    @DisplayName("V12 创建公文模板、文号规则、发文、收文、附件与归档映射表")
    void v12ShouldCreateDocumentTables() throws IOException {
        Path v12 = MIGRATION_DIR.resolve("V12__document.sql");
        Set<String> tables = extractTables(v12);
        assertEquals(Set.of("oa_doc_template", "oa_doc_no_rule", "oa_doc_outgoing",
                "oa_doc_incoming", "oa_doc_attachment", "oa_doc_archive_ref"), tables,
                "V12 建表集合与 M4 公文设计不一致");

        String script = Files.readString(v12, StandardCharsets.UTF_8);
        for (String column : List.of("tenant_id", "code", "doc_type", "version", "header_text",
                "footer_text", "body_template", "prefix", "period_format", "serial_length",
                "doc_no", "title", "security_level", "urgency", "drafter_id", "main_recipients",
                "copy_recipients", "signer", "incoming_no", "source_unit", "source_no",
                "received_date", "assignee_id", "instruction", "file_id", "source_id",
                "archive_item_id", "error_message")) {
            assertTrue(script.contains(column), "V12 公文表应包含列：" + column);
        }
        assertTrue(script.contains("uk_oa_doc_template_code_version"), "V12 应保证模板编码+版本唯一");
        assertTrue(script.contains("uk_oa_doc_no_rule_code"), "V12 应保证文号规则编码唯一");
        assertTrue(script.contains("uk_oa_doc_outgoing_no"), "V12 应保证发文文号租户唯一");
        assertTrue(script.contains("uk_oa_doc_incoming_no"), "V12 应保证收文文号租户唯一");
        assertTrue(script.contains("uk_oa_doc_archive_doc"), "V12 应保证每单归档映射唯一");
        assertTrue(script.contains("'DOC_OUTGOING'"), "V12 应种子发文审批业务类型");
        assertTrue(script.contains("'OA_DOC_OUTGOING'"), "V12 应绑定发文流程定义");
        assertTrue(script.contains("'OUT_DEFAULT'"), "V12 应种子默认发文文号规则");
        assertTrue(script.contains("'IN_DEFAULT'"), "V12 应种子默认收文文号规则");
    }

    @Test
    @DisplayName("V13 创建企业文化、投票与建议 7 张表")
    void v13ShouldCreateCultureTables() throws IOException {
        Path v13 = MIGRATION_DIR.resolve("V13__culture.sql");
        Set<String> tables = extractTables(v13);
        assertEquals(Set.of("oa_culture_event", "oa_culture_wish", "oa_poll", "oa_poll_option",
                "oa_poll_vote", "oa_suggestion", "oa_suggestion_reply"), tables,
                "V13 建表集合与 M5-7d-1 文化设计不一致");

        String script = Files.readString(v13, StandardCharsets.UTF_8);
        for (String column : List.of("event_type", "wish_type", "multiple_choice", "anonymous",
                "max_choices", "option_text", "option_id", "submitter_id", "replier_id")) {
            assertTrue(script.contains(column), "V13 文化表应包含列：" + column);
        }
        assertTrue(script.contains("uk_oa_poll_vote_option"), "V13 应保证同一用户对同一选项仅投一次");
        assertTrue(script.contains("uk_oa_culture_wish_once"), "V13 应保证当天同类型同对象同发送人唯一");
    }

    @Test
    @DisplayName("V14 创建人员异动单与离职清单任务 2 张表")
    void v14ShouldCreateEmployeeChangeTables() throws IOException {
        Path v14 = MIGRATION_DIR.resolve("V14__hr_change.sql");
        Set<String> tables = extractTables(v14);
        assertEquals(Set.of("oa_employee_change", "oa_employee_change_task"), tables,
                "V14 建表集合与 M5-7d-2 人员异动设计不一致");

        String script = Files.readString(v14, StandardCharsets.UTF_8);
        for (String column : List.of("change_no", "change_type", "user_id", "employee_name",
                "from_dept_id", "to_dept_id", "from_status", "to_status", "effective_date",
                "approval_instance_id", "applicant_id", "executor_id", "executed_time",
                "execute_result", "task_code", "task_name", "done_time")) {
            assertTrue(script.contains(column), "V14 人员异动表应包含列：" + column);
        }
        assertTrue(script.contains("uk_oa_employee_change_no"), "V14 应保证异动单号租户唯一");
        assertTrue(script.contains("uk_oa_employee_change_task"), "V14 应保证同一异动单任务编码唯一");
        assertTrue(script.contains("'HR_CHANGE'"), "V14 应种子 HR_CHANGE 业务类型");
        assertTrue(script.contains("'OA_HR_CHANGE'"), "V14 应绑定 OA_HR_CHANGE 流程定义");
        assertTrue(script.contains("DEPT_LEADER"), "V14 应种子部门负责人审批规则");
        assertTrue(script.contains("'oa_hr_admin'"), "V14 应种子 HR 角色审批规则");
    }

    /**
     * 列出迁移脚本并按版本号数值升序返回（避免 V10 排在 V1 之前）
     *
     * @return 迁移脚本路径
     * @throws IOException 读取目录失败
     */
    private static List<Path> migrationScripts() throws IOException {
        try (var stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingLong(OaFlywayMigrationScriptTest::versionOf))
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