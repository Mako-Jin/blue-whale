package com.bluewhale.oa.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * OA 业务错误码。
 *
 * <p>段位约定：{@code 400xx} 参数与业务校验失败，{@code 404xx} 资源不存在
 * （跨租户一律按不存在处理），{@code 409xx} 状态或唯一性冲突。
 * 存量请假接口继续使用通用 {@code 400/404} 错误码，保证旧前端行为不变。</p>
 *
 * @author BlueWhale
 */
public final class OaErrorCode {

    /** 审批配置不存在 */
    public static final int APPROVAL_CONFIG_NOT_FOUND = 40401;

    /** 审批配置业务类型已存在 */
    public static final int APPROVAL_CONFIG_EXISTS = 40901;

    /** 审批配置参数非法 */
    public static final int APPROVAL_CONFIG_INVALID = 40001;

    /** 审批实例不存在 */
    public static final int APPROVAL_INSTANCE_NOT_FOUND = 40402;

    /** 审批实例状态不允许该操作 */
    public static final int APPROVAL_STATUS_CONFLICT = 40902;

    /** 审批结果非法 */
    public static final int APPROVAL_RESULT_INVALID = 40002;

    /** 审批实例缺少 Workflow 实例ID */
    public static final int APPROVAL_WORKFLOW_NOT_BOUND = 40903;

    /** 审批人规则不存在 */
    public static final int APPROVAL_RULE_NOT_FOUND = 40403;

    /** 审批人规则参数非法 */
    public static final int APPROVAL_RULE_INVALID = 40003;

    /** 审批副作用不存在 */
    public static final int APPROVAL_EFFECT_NOT_FOUND = 40404;

    /** 审批副作用已结束，不能重复执行 */
    public static final int APPROVAL_EFFECT_CLOSED = 40904;

    /** 文件引用已存在 */
    public static final int FILE_REF_EXISTS = 40905;

    /** 文件引用不存在 */
    public static final int FILE_REF_NOT_FOUND = 40405;

    /** 文件引用参数非法 */
    public static final int FILE_REF_INVALID = 40004;

    /** 文件引用禁止：不是本人上传的文件 */
    public static final int FILE_REF_FORBIDDEN = 40307;

    /** 文件引用登记时文件服务不可用 */
    public static final int FILE_REF_UNAVAILABLE = 50310;

    /** 序列号参数非法 */
    public static final int SEQUENCE_INVALID = 40005;

    /** Outbox 事件已存在 */
    public static final int OUTBOX_EVENT_EXISTS = 40906;

    /** Outbox 事件不存在 */
    public static final int OUTBOX_EVENT_NOT_FOUND = 40406;

    /** 员工档案不存在 */
    public static final int EMPLOYEE_PROFILE_NOT_FOUND = 40407;

    /** 员工档案参数非法 */
    public static final int EMPLOYEE_PROFILE_INVALID = 40006;

    /** 假期类型不存在 */
    public static final int LEAVE_TYPE_NOT_FOUND = 40408;

    /** 假期类型编码已存在 */
    public static final int LEAVE_TYPE_EXISTS = 40907;

    /** 假期类型参数非法 */
    public static final int LEAVE_TYPE_INVALID = 40007;

    /** 假期额度不存在 */
    public static final int LEAVE_BALANCE_NOT_FOUND = 40409;

    /** 假期额度不足 */
    public static final int LEAVE_BALANCE_INSUFFICIENT = 40908;

    /** 假期额度参数非法 */
    public static final int LEAVE_BALANCE_INVALID = 40008;

    /** 请假申请不存在 */
    public static final int LEAVE_NOT_FOUND = 40410;

    /** 请假申请状态不允许该操作 */
    public static final int LEAVE_STATUS_CONFLICT = 40909;

    /** 请假时间区间与其他申请重叠 */
    public static final int LEAVE_PERIOD_OVERLAP = 40910;

    /** 请假参数非法 */
    public static final int LEAVE_INVALID = 40009;

    /** 工作日历参数非法 */
    public static final int WORK_CALENDAR_INVALID = 40010;

    /** 加班申请参数非法 */
    public static final int OVERTIME_INVALID = 40011;

    /** 加班申请不存在 */
    public static final int OVERTIME_NOT_FOUND = 40414;

    /** 加班申请状态不允许该操作 */
    public static final int OVERTIME_STATUS_CONFLICT = 40911;

    /** 加班时段与其他申请重叠 */
    public static final int OVERTIME_PERIOD_OVERLAP = 40912;

    /** 月度加班时长超限 */
    public static final int OVERTIME_MONTHLY_LIMIT = 40913;

    /** 出差申请参数非法 */
    public static final int TRIP_INVALID = 40012;

    /** 出差申请不存在 */
    public static final int TRIP_NOT_FOUND = 40415;

    /** 出差申请状态不允许该操作 */
    public static final int TRIP_STATUS_CONFLICT = 40914;

    /** 出差起止日期非法 */
    public static final int TRIP_INVALID_PERIOD = 40915;

    /** 出差报告不允许提交 */
    public static final int TRIP_REPORT_NOT_ALLOWED = 40916;

    /** 无权操作该审批任务 */
    public static final int APPROVAL_ACTION_FORBIDDEN = 40301;

    /** 审批任务不存在或已不可操作 */
    public static final int APPROVAL_TASK_NOT_FOUND = 40411;

    /** 代理规则不存在或无权访问 */
    public static final int APPROVAL_DELEGATE_NOT_FOUND = 40412;

    /** 抄送记录不存在或无权访问 */
    public static final int APPROVAL_CC_NOT_FOUND = 40413;

    /** 流程服务暂不可用 */
    public static final int APPROVAL_WORKFLOW_UNAVAILABLE = 50301;

    /** 考勤参数非法 */
    public static final int ATT_INVALID = 40013;

    /** 考勤主数据不存在 */
    public static final int ATT_CONFIG_NOT_FOUND = 40416;

    /** 考勤主数据编码已存在 */
    public static final int ATT_CONFIG_EXISTS = 40917;

    /** 班次不存在 */
    public static final int ATT_SHIFT_NOT_FOUND = 40417;

    /** 重复打卡 */
    public static final int ATT_RECORD_DUPLICATE = 40918;

    /** 补卡次数超过月度上限 */
    public static final int ATT_CORRECTION_LIMIT = 40919;

    /** 补卡申请不存在 */
    public static final int ATT_CORRECTION_NOT_FOUND = 40418;

    /** 补卡申请状态不允许该操作 */
    public static final int ATT_CORRECTION_STATUS_CONFLICT = 40920;

    /** 考勤异常不存在 */
    public static final int ATT_EXCEPTION_NOT_FOUND = 40424;

    /** 报销参数非法 */
    public static final int EXPENSE_INVALID = 40014;

    /** 报销单不存在 */
    public static final int EXPENSE_NOT_FOUND = 40419;

    /** 报销单状态不允许该操作 */
    public static final int EXPENSE_STATUS_CONFLICT = 40921;

    /** 发票重复 */
    public static final int EXPENSE_INVOICE_DUPLICATE = 40922;

    /** 超费用标准 */
    public static final int EXPENSE_STANDARD_EXCEEDED = 40923;

    /** 费用类别不存在 */
    public static final int EXPENSE_CATEGORY_NOT_FOUND = 40420;

    /** 费用类别编码已存在 */
    public static final int EXPENSE_CATEGORY_EXISTS = 40924;

    /** ERP 服务暂不可用 */
    public static final int EXPENSE_ERP_UNAVAILABLE = 50302;

    /** 用车参数非法 */
    public static final int VEHICLE_INVALID = 40015;

    /** 用车预约不存在 */
    public static final int VEHICLE_BOOKING_NOT_FOUND = 40421;

    /** 用车预约状态不允许该操作 */
    public static final int VEHICLE_BOOKING_STATUS_CONFLICT = 40925;

    /** 同一车辆时段冲突 */
    public static final int VEHICLE_BOOKING_CONFLICT = 40926;

    /** 用章参数非法 */
    public static final int SEAL_INVALID = 40016;

    /** 无该印章使用授权 */
    public static final int SEAL_NOT_AUTHORIZED = 40302;

    /** 用章已登记 */
    public static final int SEAL_ALREADY_REGISTERED = 40927;

    /** 用章申请不存在 */
    public static final int SEAL_NOT_FOUND = 40425;

    /** 用章申请状态不允许该操作 */
    public static final int SEAL_STATUS_CONFLICT = 40928;

    /** 合同服务暂不可用 */
    public static final int SEAL_CONTRACT_UNAVAILABLE = 50303;

    /** 采购申请参数非法 */
    public static final int PURCHASE_INVALID = 40017;

    /** 采购申请不存在 */
    public static final int PURCHASE_NOT_FOUND = 40426;

    /** 采购申请状态不允许该操作 */
    public static final int PURCHASE_STATUS_CONFLICT = 40929;

    /** ERP 采购订单推送暂不可用 */
    public static final int PURCHASE_ERP_UNAVAILABLE = 50304;

    /** 协同参数非法 */
    public static final int COLLAB_INVALID = 40018;

    /** 会议室不存在 */
    public static final int MEETING_ROOM_NOT_FOUND = 40427;

    /** 会议室名称已存在 */
    public static final int MEETING_ROOM_EXISTS = 40930;

    /** 会议不存在 */
    public static final int MEETING_NOT_FOUND = 40428;

    /** 会议状态不允许该操作 */
    public static final int MEETING_STATUS_CONFLICT = 40931;

    /** 会议室时段冲突 */
    public static final int MEETING_TIME_CONFLICT = 40932;

    /** 会议参会人缺失 */
    public static final int MEETING_PARTICIPANT_REQUIRED = 40933;

    /** 公告参数非法 */
    public static final int NOTICE_INVALID = 40019;

    /** 公告不存在 */
    public static final int NOTICE_NOT_FOUND = 40429;

    /** 公告状态不允许该操作 */
    public static final int NOTICE_STATUS_CONFLICT = 40934;

    /** 公告接收人为空 */
    public static final int NOTICE_RECIPIENT_EMPTY = 40935;

    /** 公告接收人不存在或无权访问 */
    public static final int NOTICE_RECIPIENT_NOT_FOUND = 40430;

    /** 任务参数非法 */
    public static final int TASK_INVALID = 40020;

    /** 任务不存在 */
    public static final int TASK_NOT_FOUND = 40431;

    /** 任务状态不允许该操作 */
    public static final int TASK_STATUS_CONFLICT = 40936;

    /** 日程参数非法 */
    public static final int CALENDAR_INVALID = 40021;

    /** 日程不存在或无权访问 */
    public static final int CALENDAR_NOT_FOUND = 40432;

    /** 无权操作该日程 */
    public static final int CALENDAR_FORBIDDEN = 40303;

    /** 网盘参数非法 */
    public static final int DRIVE_INVALID = 40022;

    /** 网盘文件夹不存在或无权访问 */
    public static final int DRIVE_FOLDER_NOT_FOUND = 40433;

    /** 网盘文件不存在或无权访问 */
    public static final int DRIVE_FILE_NOT_FOUND = 40434;

    /** 无权操作该网盘资源 */
    public static final int DRIVE_FORBIDDEN = 40304;

    /** 网盘文件夹非空，不能删除 */
    public static final int DRIVE_FOLDER_NOT_EMPTY = 40937;

    /** 网盘配额已超限 */
    public static final int DRIVE_QUOTA_EXCEEDED = 40938;

    /** 该文件已在当前文件夹登记 */
    public static final int DRIVE_FILE_EXISTS = 40939;

    /** 同目录下文件夹名称已存在 */
    public static final int DRIVE_NAME_EXISTS = 40940;

    /** 文件服务不可用或文件不可下载 */
    public static final int DRIVE_FILE_UNAVAILABLE = 50305;

    /** 通讯录服务不可用 */
    public static final int DIRECTORY_UNAVAILABLE = 50306;

    /** 公文参数非法 */
    public static final int DOC_INVALID = 40023;

    /** 公文不存在或无权访问 */
    public static final int DOC_NOT_FOUND = 40435;

    /** 公文当前状态不允许该操作 */
    public static final int DOC_STATUS_CONFLICT = 40941;

    /** 文号规则不存在或无权访问 */
    public static final int DOC_NO_RULE_NOT_FOUND = 40436;

    /** 文号规则编码已存在 */
    public static final int DOC_NO_RULE_EXISTS = 40942;

    /** 公文模板不存在或无权访问 */
    public static final int DOC_TEMPLATE_NOT_FOUND = 40437;

    /** 公文模板同编码同版本已存在 */
    public static final int DOC_TEMPLATE_EXISTS = 40943;

    /** 无权操作该公文 */
    public static final int DOC_FORBIDDEN = 40305;

    /** 档案服务暂不可用 */
    public static final int DOC_ARCHIVE_UNAVAILABLE = 50307;

    /** 文件服务不可用或附件不可下载 */
    public static final int DOC_FILE_UNAVAILABLE = 50308;

    /** 文化动态参数非法 */
    public static final int CULTURE_INVALID = 40024;

    /** 文化动态不存在或无权访问 */
    public static final int CULTURE_EVENT_NOT_FOUND = 40438;

    /** 文化动态当前状态不允许该操作 */
    public static final int CULTURE_EVENT_STATUS_CONFLICT = 40944;

    /** 投票参数非法 */
    public static final int POLL_INVALID = 40025;

    /** 投票不存在或无权访问 */
    public static final int POLL_NOT_FOUND = 40439;

    /** 投票当前状态不允许该操作 */
    public static final int POLL_STATUS_CONFLICT = 40945;

    /** 同一用户已参与该投票 */
    public static final int POLL_ALREADY_VOTED = 40946;

    /** 投票选项缺失或不属于该投票 */
    public static final int POLL_OPTION_REQUIRED = 40947;

    /** 建议参数非法 */
    public static final int SUGGESTION_INVALID = 40026;

    /** 建议不存在或无权访问 */
    public static final int SUGGESTION_NOT_FOUND = 40440;

    /** 建议当前状态不允许该操作 */
    public static final int SUGGESTION_STATUS_CONFLICT = 40948;

    /** 人员异动参数非法 */
    public static final int HR_CHANGE_INVALID = 40027;

    /** 人员异动单不存在或无权访问 */
    public static final int HR_CHANGE_NOT_FOUND = 40441;

    /** 人员异动单当前状态不允许该操作 */
    public static final int HR_CHANGE_STATUS_CONFLICT = 40949;

    /** 四眼原则：申请人与执行人不能为同一人 */
    public static final int HR_CHANGE_FOUR_EYES = 40306;

    /** Auth 人员生命周期服务暂不可用 */
    public static final int HR_CHANGE_AUTH_UNAVAILABLE = 50309;

    /** 报表参数非法（报表编码、期间或日期区间） */
    public static final int REPORT_INVALID = 40028;

    /** 报表快照不存在或无权访问 */
    public static final int REPORT_SNAPSHOT_NOT_FOUND = 40442;


    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(APPROVAL_CONFIG_NOT_FOUND, "审批配置不存在或无权访问"),
            Map.entry(APPROVAL_CONFIG_EXISTS, "该业务类型已存在审批配置"),
            Map.entry(APPROVAL_CONFIG_INVALID, "审批配置参数非法，请检查业务类型与流程定义"),
            Map.entry(APPROVAL_INSTANCE_NOT_FOUND, "审批实例不存在或无权访问"),
            Map.entry(APPROVAL_STATUS_CONFLICT, "审批实例当前状态不允许该操作"),
            Map.entry(APPROVAL_RESULT_INVALID, "审批结果仅支持 APPROVED / REJECTED / CANCELLED / BROKEN"),
            Map.entry(APPROVAL_WORKFLOW_NOT_BOUND, "审批实例尚未绑定 Workflow 实例，请先发起流程"),
            Map.entry(APPROVAL_RULE_NOT_FOUND, "审批人规则不存在或无权访问"),
            Map.entry(APPROVAL_RULE_INVALID, "审批人规则参数非法，请检查节点与解析器"),
            Map.entry(APPROVAL_EFFECT_NOT_FOUND, "审批副作用不存在或无权访问"),
            Map.entry(APPROVAL_EFFECT_CLOSED, "审批副作用已结束，不能重复执行"),
            Map.entry(FILE_REF_EXISTS, "该文件已关联到当前业务"),
            Map.entry(FILE_REF_NOT_FOUND, "文件引用不存在或无权访问"),
            Map.entry(FILE_REF_INVALID, "文件引用参数非法"),
            Map.entry(FILE_REF_FORBIDDEN, "只能引用本人上传的文件"),
            Map.entry(FILE_REF_UNAVAILABLE, "文件服务暂不可用，附件登记失败，请稍后重试"),
            Map.entry(SEQUENCE_INVALID, "序列号参数非法"),
            Map.entry(OUTBOX_EVENT_EXISTS, "集成事件已存在"),
            Map.entry(OUTBOX_EVENT_NOT_FOUND, "集成事件不存在或无权访问"),
            Map.entry(EMPLOYEE_PROFILE_NOT_FOUND, "员工档案不存在或无权访问"),
            Map.entry(EMPLOYEE_PROFILE_INVALID, "员工档案参数非法，请检查用户与在职状态"),
            Map.entry(LEAVE_TYPE_NOT_FOUND, "假期类型不存在或无权访问"),
            Map.entry(LEAVE_TYPE_EXISTS, "假期类型编码已存在"),
            Map.entry(LEAVE_TYPE_INVALID, "假期类型参数非法，请检查编码/名称/单位与额度"),
            Map.entry(LEAVE_BALANCE_NOT_FOUND, "假期额度不存在或无权访问"),
            Map.entry(LEAVE_BALANCE_INSUFFICIENT, "假期额度不足，无法提交申请"),
            Map.entry(LEAVE_BALANCE_INVALID, "假期额度参数非法"),
            Map.entry(LEAVE_NOT_FOUND, "请假申请不存在或无权访问"),
            Map.entry(LEAVE_STATUS_CONFLICT, "请假申请当前状态不允许该操作"),
            Map.entry(LEAVE_PERIOD_OVERLAP, "同一假期类型在所选时段内已有未结束的申请"),
            Map.entry(LEAVE_INVALID, "请假参数非法，请检查日期、时段与假期类型"),
            Map.entry(WORK_CALENDAR_INVALID, "工作日历参数非法，请检查日期与工时"),
            Map.entry(OVERTIME_INVALID, "加班参数非法，请检查日期、起止时间与补偿方式"),
            Map.entry(OVERTIME_NOT_FOUND, "加班申请不存在或无权访问"),
            Map.entry(OVERTIME_STATUS_CONFLICT, "加班申请当前状态不允许该操作"),
            Map.entry(OVERTIME_PERIOD_OVERLAP, "所选时段与当天已有加班申请重叠"),
            Map.entry(OVERTIME_MONTHLY_LIMIT, "本月加班时长已超过上限"),
            Map.entry(TRIP_INVALID, "出差参数非法，请检查目的地、金额与行程"),
            Map.entry(TRIP_NOT_FOUND, "出差申请不存在或无权访问"),
            Map.entry(TRIP_STATUS_CONFLICT, "出差申请当前状态不允许该操作"),
            Map.entry(TRIP_INVALID_PERIOD, "出差起止日期非法"),
            Map.entry(TRIP_REPORT_NOT_ALLOWED, "出差报告当前不允许提交"),
            Map.entry(APPROVAL_ACTION_FORBIDDEN, "无权操作该审批任务"),
            Map.entry(APPROVAL_TASK_NOT_FOUND, "审批任务不存在或已不可操作"),
            Map.entry(APPROVAL_DELEGATE_NOT_FOUND, "代理规则不存在或无权访问"),
            Map.entry(APPROVAL_CC_NOT_FOUND, "抄送记录不存在或无权访问"),
            Map.entry(APPROVAL_WORKFLOW_UNAVAILABLE, "流程服务暂不可用，请稍后重试"),
            Map.entry(ATT_INVALID, "考勤参数非法，请检查时间、类型与来源"),
            Map.entry(ATT_CONFIG_NOT_FOUND, "考勤配置不存在或无权访问"),
            Map.entry(ATT_CONFIG_EXISTS, "考勤配置编码已存在"),
            Map.entry(ATT_SHIFT_NOT_FOUND, "班次不存在或无权访问"),
            Map.entry(ATT_RECORD_DUPLICATE, "该时间已存在相同类型的打卡记录"),
            Map.entry(ATT_CORRECTION_LIMIT, "本月补卡次数已超过上限"),
            Map.entry(ATT_CORRECTION_NOT_FOUND, "补卡申请不存在或无权访问"),
            Map.entry(ATT_CORRECTION_STATUS_CONFLICT, "补卡申请当前状态不允许该操作"),
            Map.entry(ATT_EXCEPTION_NOT_FOUND, "考勤异常不存在或无权访问"),
            Map.entry(EXPENSE_INVALID, "报销参数非法，请检查明细、发票与金额"),
            Map.entry(EXPENSE_NOT_FOUND, "报销单不存在或无权访问"),
            Map.entry(EXPENSE_STATUS_CONFLICT, "报销单当前状态不允许该操作"),
            Map.entry(EXPENSE_INVOICE_DUPLICATE, "发票已在其他报销单中使用"),
            Map.entry(EXPENSE_STANDARD_EXCEEDED, "超出费用标准，请调整后提交"),
            Map.entry(EXPENSE_CATEGORY_NOT_FOUND, "费用类别不存在或无权访问"),
            Map.entry(EXPENSE_CATEGORY_EXISTS, "费用类别编码已存在"),
            Map.entry(EXPENSE_ERP_UNAVAILABLE, "ERP 服务暂不可用，稍后可重试推送"),
            Map.entry(VEHICLE_INVALID, "用车参数非法，请检查时间、车辆与司机"),
            Map.entry(VEHICLE_BOOKING_NOT_FOUND, "用车预约不存在或无权访问"),
            Map.entry(VEHICLE_BOOKING_STATUS_CONFLICT, "用车预约当前状态不允许该操作"),
            Map.entry(VEHICLE_BOOKING_CONFLICT, "该车辆在所选时段已有预约"),
            Map.entry(SEAL_INVALID, "用章参数非法，请检查印章与文件"),
            Map.entry(SEAL_NOT_AUTHORIZED, "无该印章的使用授权"),
            Map.entry(SEAL_ALREADY_REGISTERED, "该用章申请已登记"),
            Map.entry(SEAL_NOT_FOUND, "用章申请不存在或无权访问"),
            Map.entry(SEAL_STATUS_CONFLICT, "用章申请当前状态不允许该操作"),
            Map.entry(SEAL_CONTRACT_UNAVAILABLE, "合同服务暂不可用，请稍后重试"),
            Map.entry(PURCHASE_INVALID, "采购申请参数非法，请检查明细与金额"),
            Map.entry(PURCHASE_NOT_FOUND, "采购申请不存在或无权访问"),
            Map.entry(PURCHASE_STATUS_CONFLICT, "采购申请当前状态不允许该操作"),
            Map.entry(PURCHASE_ERP_UNAVAILABLE, "ERP 采购订单服务暂不可用，稍后可重试推送"),
            Map.entry(COLLAB_INVALID, "协同参数非法，请检查时间、范围与人员"),
            Map.entry(MEETING_ROOM_NOT_FOUND, "会议室不存在或无权访问"),
            Map.entry(MEETING_ROOM_EXISTS, "会议室名称已存在"),
            Map.entry(MEETING_NOT_FOUND, "会议不存在或无权访问"),
            Map.entry(MEETING_STATUS_CONFLICT, "会议当前状态不允许该操作"),
            Map.entry(MEETING_TIME_CONFLICT, "该会议室在所选时段已有会议"),
            Map.entry(MEETING_PARTICIPANT_REQUIRED, "会议参会人不能为空"),
            Map.entry(NOTICE_INVALID, "公告参数非法，请检查标题、正文与发布范围"),
            Map.entry(NOTICE_NOT_FOUND, "公告不存在或无权访问"),
            Map.entry(NOTICE_STATUS_CONFLICT, "公告当前状态不允许该操作"),
            Map.entry(NOTICE_RECIPIENT_EMPTY, "公告接收人不能为空"),
            Map.entry(NOTICE_RECIPIENT_NOT_FOUND, "公告接收记录不存在或无权访问"),
            Map.entry(TASK_INVALID, "任务参数非法，请检查标题、负责人与截止日期"),
            Map.entry(TASK_NOT_FOUND, "任务不存在或无权访问"),
            Map.entry(TASK_STATUS_CONFLICT, "任务当前状态不允许该操作"),
            Map.entry(CALENDAR_INVALID, "日程参数非法，请检查时间区间、可见性与提醒设置"),
            Map.entry(CALENDAR_NOT_FOUND, "日程不存在或无权访问"),
            Map.entry(CALENDAR_FORBIDDEN, "无权操作该日程"),
            Map.entry(DRIVE_INVALID, "网盘参数非法，请检查文件夹名称、范围与授权设置"),
            Map.entry(DRIVE_FOLDER_NOT_FOUND, "网盘文件夹不存在或无权访问"),
            Map.entry(DRIVE_FILE_NOT_FOUND, "网盘文件不存在或无权访问"),
            Map.entry(DRIVE_FORBIDDEN, "无权操作该网盘资源"),
            Map.entry(DRIVE_FOLDER_NOT_EMPTY, "文件夹下仍有子文件夹或文件，不能删除"),
            Map.entry(DRIVE_QUOTA_EXCEEDED, "网盘配额已超限，请清理后重试"),
            Map.entry(DRIVE_FILE_EXISTS, "该文件已在当前文件夹登记"),
            Map.entry(DRIVE_NAME_EXISTS, "同目录下已存在同名文件夹"),
            Map.entry(DRIVE_FILE_UNAVAILABLE, "文件服务暂不可用或文件不可下载，请稍后重试"),
            Map.entry(DIRECTORY_UNAVAILABLE, "通讯录服务暂不可用，请稍后重试"),
            Map.entry(DOC_INVALID, "公文参数非法，请检查标题、文种与收发单位"),
            Map.entry(DOC_NOT_FOUND, "公文不存在或无权访问"),
            Map.entry(DOC_STATUS_CONFLICT, "公文当前状态不允许该操作"),
            Map.entry(DOC_NO_RULE_NOT_FOUND, "文号规则不存在或无权访问"),
            Map.entry(DOC_NO_RULE_EXISTS, "文号规则编码已存在"),
            Map.entry(DOC_TEMPLATE_NOT_FOUND, "公文模板不存在或无权访问"),
            Map.entry(DOC_TEMPLATE_EXISTS, "公文模板同编码同版本已存在"),
            Map.entry(DOC_FORBIDDEN, "无权操作该公文"),
            Map.entry(DOC_ARCHIVE_UNAVAILABLE, "档案服务暂不可用，稍后可重试归档"),
            Map.entry(DOC_FILE_UNAVAILABLE, "文件服务暂不可用或附件不可下载，请稍后重试"),
            Map.entry(CULTURE_INVALID, "文化动态参数非法，请检查类型、标题与日期"),
            Map.entry(CULTURE_EVENT_NOT_FOUND, "文化动态不存在或无权访问"),
            Map.entry(CULTURE_EVENT_STATUS_CONFLICT, "文化动态当前状态不允许该操作"),
            Map.entry(POLL_INVALID, "投票参数非法，请检查选项、时间与可选数量"),
            Map.entry(POLL_NOT_FOUND, "投票不存在或无权访问"),
            Map.entry(POLL_STATUS_CONFLICT, "投票当前状态不允许该操作"),
            Map.entry(POLL_ALREADY_VOTED, "您已参与该投票，不能重复投票"),
            Map.entry(POLL_OPTION_REQUIRED, "投票选项不能为空或不属于该投票"),
            Map.entry(SUGGESTION_INVALID, "建议参数非法，请检查标题、类别与内容"),
            Map.entry(SUGGESTION_NOT_FOUND, "建议不存在或无权访问"),
            Map.entry(SUGGESTION_STATUS_CONFLICT, "建议当前状态不允许该操作"),
            Map.entry(HR_CHANGE_INVALID, "人员异动参数非法，请检查类型、员工、部门与生效日期"),
            Map.entry(HR_CHANGE_NOT_FOUND, "人员异动单不存在或无权访问"),
            Map.entry(HR_CHANGE_STATUS_CONFLICT, "人员异动单当前状态不允许该操作"),
            Map.entry(HR_CHANGE_FOUR_EYES, "四眼原则：申请人与执行人不能为同一人"),
            Map.entry(HR_CHANGE_AUTH_UNAVAILABLE, "Auth 人员生命周期服务暂不可用，稍后可重试执行"),
            Map.entry(REPORT_INVALID, "报表参数非法，请检查报表类型、统计期间与日期区间"),
            Map.entry(REPORT_SNAPSHOT_NOT_FOUND, "报表快照不存在或无权访问"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private OaErrorCode() {
    }

    /**
     * 查询错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "OA 操作失败");
    }

    /**
     * 以错误码构造业务异常。
     *
     * @param code 业务错误码
     * @return 业务异常
     */
    public static BizException exception(int code) {
        return new BizException(code, messageOf(code));
    }

    /**
     * 以错误码与自定义消息构造业务异常。
     *
     * @param code 业务错误码
     * @param message 自定义提示语
     * @return 业务异常
     */
    public static BizException exception(int code, String message) {
        return new BizException(code, message);
    }
}
