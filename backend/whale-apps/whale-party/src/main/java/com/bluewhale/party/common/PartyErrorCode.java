package com.bluewhale.party.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 党政工会业务错误码。
 *
 * <p>段位约定：{@code 43xxx} 归 {@code whale-party}，与 LMS 的 42xxx 并列不交叉。
 * 43000–43009 为门禁、身份、编号流水与支部 / 党员 / 组织生活基础；
 * 43010–43029 为党费与党建台账其余；43030–43049 为党建内容与先审后发；
 * 43050–43079 为工会（会员 / 慰问 / 福利 / 活动 / 提案）；
 * 43080–43089 为跨服务契约（ERP / 文件 / 通知 / 审批）；43090–43099 为看板统计、
 * 通知扫描与员工快照同步；43100 起留给后续上下文。</p>
 *
 * @author BlueWhale
 */
public final class PartyErrorCode {

    /** 需求门禁未通过，业务接口未开放 */
    public static final int GATE_CLOSED = 43000;

    /** 缺少可信操作人身份，写操作失败关闭 */
    public static final int ACTOR_REQUIRED = 43001;

    /** 业务编号取号参数非法（前缀为空、租户缺失或取号次数耗尽） */
    public static final int SEQUENCE_INVALID = 43002;

    /** 支部不存在或无权访问（跨租户一律按不存在处理） */
    public static final int BRANCH_NOT_FOUND = 43003;

    /** 支部编码已存在 */
    public static final int BRANCH_CODE_CONFLICT = 43004;

    /** 上级支部非法：自指或形成环 */
    public static final int BRANCH_PARENT_CYCLE = 43005;

    /** 党员档案不存在或无权访问 */
    public static final int MEMBER_NOT_FOUND = 43006;

    /** 该员工已有党员档案（一人一档） */
    public static final int MEMBER_DUPLICATED = 43007;

    /** 组织生活记录不存在或无权访问 */
    public static final int ACTIVITY_NOT_FOUND = 43008;

    /** 组织生活状态不允许该操作（已归档不可再改记录） */
    public static final int ACTIVITY_STATE_CONFLICT = 43009;

    /** 党费参数非法（基数、比例或所属月份越界） */
    public static final int FEE_INVALID = 43010;

    /** 党员状态不允许该操作（已转出只读、已停籍不可缴纳） */
    public static final int MEMBER_STATE_CONFLICT = 43011;

    /** 未匹配到党费标准（基数落在所有档位之外） */
    public static final int FEE_RULE_MISSING = 43012;

    /** 该月党费已生成，不重复生成 */
    public static final int FEE_BILL_DUPLICATED = 43013;

    /** 党费账单不存在或无权访问 */
    public static final int FEE_BILL_NOT_FOUND = 43014;

    /** 支部已停用，不可再接收党员或新建组织生活 */
    public static final int BRANCH_INACTIVE = 43015;

    /** 党费标准区间重叠（同一基数会命中多条规则） */
    public static final int FEE_RULE_OVERLAP = 43016;

    /** 支部当前状态不允许该操作（停用前须先转出党员与下级支部） */
    public static final int BRANCH_STATE_CONFLICT = 43017;

    /** 支部字段非法（编码 / 名称 / 备注为空或超长） */
    public static final int BRANCH_INVALID = 43018;

    /** 党员档案字段非法（编号/工号/姓名/日期/基数越界） */
    public static final int MEMBER_INVALID = 43019;

    /** 党建内容不存在或无权访问 */
    public static final int CONTENT_NOT_FOUND = 43030;

    /** 党建内容状态不允许该操作（如待审不可改、已下线不可再流转） */
    public static final int CONTENT_STATE_CONFLICT = 43031;

    /** 党建内容字段非法（分类/标题/摘要/正文/附件越界） */
    public static final int CONTENT_INVALID = 43032;

    /** 审核退回必须填写意见 */
    public static final int CONTENT_AUDIT_OPINION_REQUIRED = 43033;

    /** 无权查看该党建内容（未审仅作者与审核人可见） */
    public static final int CONTENT_FORBIDDEN = 43034;

    /** 可见范围非法（指定支部未选支部或超出上限） */
    public static final int CONTENT_VISIBILITY_INVALID = 43035;

    /** 指定支部不存在或已停用 */
    public static final int CONTENT_BRANCH_INVALID = 43036;

    /** 工会会员不存在或无权访问 */
    public static final int UNION_MEMBER_NOT_FOUND = 43050;

    /** 该员工已有在册会籍（退会后可再入会，但同时在册只能一份） */
    public static final int UNION_MEMBER_DUPLICATED = 43051;

    /** 工会会员字段非法（工号/姓名/日期/会费基数越界） */
    public static final int UNION_MEMBER_INVALID = 43052;

    /** 会籍状态不允许该操作（已退会只读） */
    public static final int UNION_MEMBER_STATE_CONFLICT = 43053;

    /** 慰问帮扶申请不存在或无权访问 */
    public static final int UNION_RELIEF_NOT_FOUND = 43054;

    /** 慰问帮扶字段非法（类型/金额/事由越界） */
    public static final int UNION_RELIEF_INVALID = 43055;

    /** 同人同年同类型慰问超出次数或金额上限 */
    public static final int UNION_RELIEF_LIMIT_EXCEEDED = 43056;

    /** 慰问帮扶状态不允许该操作 */
    public static final int UNION_RELIEF_STATE_CONFLICT = 43057;

    /** 审批服务不可达或未配置流程定义（申请保留为草稿） */
    public static final int UNION_APPROVAL_UNAVAILABLE = 43058;

    /** 审批状态同步失败 */
    public static final int UNION_APPROVAL_SYNC_FAILED = 43059;

    /** 节日福利批次不存在或无权访问 */
    public static final int UNION_WELFARE_NOT_FOUND = 43060;

    /** 节日福利字段非法（名称/年度/标准金额越界） */
    public static final int UNION_WELFARE_INVALID = 43061;

    /** 福利批次状态不允许该操作 */
    public static final int UNION_WELFARE_STATE_CONFLICT = 43062;

    /** 福利发放明细不存在 */
    public static final int UNION_WELFARE_ITEM_NOT_FOUND = 43063;

    /** 福利明细状态不允许该操作（已签收或批次已关闭） */
    public static final int UNION_WELFARE_ITEM_STATE_CONFLICT = 43064;

    /** 无权签收该福利明细（仅本人或工会管理员） */
    public static final int UNION_WELFARE_ITEM_FORBIDDEN = 43065;

    /** 工会活动不存在或无权访问 */
    public static final int UNION_ACTIVITY_NOT_FOUND = 43070;

    /** 工会活动字段非法（名称/时间/名额越界） */
    public static final int UNION_ACTIVITY_INVALID = 43071;

    /** 工会活动状态不允许该操作 */
    public static final int UNION_ACTIVITY_STATE_CONFLICT = 43072;

    /** 已报名该活动 */
    public static final int UNION_SIGNUP_DUPLICATED = 43073;

    /** 活动名额已满 */
    public static final int UNION_SIGNUP_QUOTA_EXCEEDED = 43074;

    /** 报名记录不存在 */
    public static final int UNION_SIGNUP_NOT_FOUND = 43075;

    /** 职代会提案不存在或无权访问 */
    public static final int UNION_PROPOSAL_NOT_FOUND = 43076;

    /** 提案字段非法（标题/内容越界） */
    public static final int UNION_PROPOSAL_INVALID = 43077;

    /** 提案状态不允许该流转（单向，不可跳步或回退） */
    public static final int UNION_PROPOSAL_STATE_CONFLICT = 43078;

    /** 无权操作该提案（评价仅提交人本人） */
    public static final int UNION_PROPOSAL_FORBIDDEN = 43079;

    /** 统计参数非法（统计日越界或指标范围不合法） */
    public static final int METRIC_INVALID = 43090;

    /** 导出天数超过上限（一次最多导出一段连续统计日） */
    public static final int METRIC_EXPORT_LIMIT = 43091;

    /** 通知场景非法（该接口不支持该场景或场景编码未知） */
    public static final int NOTICE_SCENE_INVALID = 43092;

    /** 通知投递失败（下游消息服务不可达或返回失败） */
    public static final int NOTICE_SEND_FAILED = 43093;

    /** ERP 员工目录拉取失败（保留旧快照，不清表） */
    public static final int EMPLOYEE_SYNC_FAILED = 43094;

    /** ERP 员工目录为空，视为异常并拒绝覆盖旧快照 */
    public static final int EMPLOYEE_SYNC_EMPTY = 43095;

    /** 业务错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(GATE_CLOSED, "需求门禁未通过，业务接口未开放"),
            Map.entry(ACTOR_REQUIRED, "缺少操作人身份，无法执行该操作"),
            Map.entry(SEQUENCE_INVALID, "业务编号生成失败，请检查编号规则"),
            Map.entry(BRANCH_NOT_FOUND, "支部不存在或无权访问"),
            Map.entry(BRANCH_CODE_CONFLICT, "支部编码已存在"),
            Map.entry(BRANCH_PARENT_CYCLE, "上级支部不能是自身或其下级"),
            Map.entry(MEMBER_NOT_FOUND, "党员档案不存在或无权访问"),
            Map.entry(MEMBER_DUPLICATED, "该员工已有党员档案"),
            Map.entry(ACTIVITY_NOT_FOUND, "组织生活记录不存在或无权访问"),
            Map.entry(ACTIVITY_STATE_CONFLICT, "组织生活记录当前状态不允许该操作"),
            Map.entry(FEE_INVALID, "党费参数不合法"),
            Map.entry(MEMBER_STATE_CONFLICT, "党员当前状态不允许该操作"),
            Map.entry(FEE_RULE_MISSING, "未匹配到党费标准，请先配置党费档位"),
            Map.entry(FEE_BILL_DUPLICATED, "该月党费已生成，请勿重复生成"),
            Map.entry(FEE_BILL_NOT_FOUND, "党费账单不存在或无权访问"),
            Map.entry(BRANCH_INACTIVE, "支部已停用，不可再接收党员或新建组织生活"),
            Map.entry(FEE_RULE_OVERLAP, "党费标准的基数区间存在重叠"),
            Map.entry(BRANCH_STATE_CONFLICT, "支部当前状态不允许该操作，停用前请先转出党员与下级支部"),
            Map.entry(BRANCH_INVALID, "支部信息不合法，请检查编码、名称与备注"),
            Map.entry(MEMBER_INVALID, "党员档案信息不合法，请检查编号、工号、姓名与日期"),
            Map.entry(CONTENT_NOT_FOUND, "党建内容不存在或无权访问"),
            Map.entry(CONTENT_STATE_CONFLICT, "党建内容当前状态不允许该操作"),
            Map.entry(CONTENT_INVALID, "党建内容信息不合法，请检查分类、标题、正文与附件"),
            Map.entry(CONTENT_AUDIT_OPINION_REQUIRED, "退回内容必须填写审核意见"),
            Map.entry(CONTENT_FORBIDDEN, "无权查看或操作该党建内容"),
            Map.entry(CONTENT_VISIBILITY_INVALID, "可见范围不合法，指定支部时至少选择一个支部"),
            Map.entry(CONTENT_BRANCH_INVALID, "指定支部不存在或已停用"),
            Map.entry(UNION_MEMBER_NOT_FOUND, "工会会员不存在或无权访问"),
            Map.entry(UNION_MEMBER_DUPLICATED, "该员工已有在册会籍，请先办理退会"),
            Map.entry(UNION_MEMBER_INVALID, "工会会员信息不合法，请检查工号、姓名与日期"),
            Map.entry(UNION_MEMBER_STATE_CONFLICT, "会籍当前状态不允许该操作"),
            Map.entry(UNION_RELIEF_NOT_FOUND, "慰问帮扶申请不存在或无权访问"),
            Map.entry(UNION_RELIEF_INVALID, "慰问帮扶申请不合法，请检查类型、金额与事由"),
            Map.entry(UNION_RELIEF_LIMIT_EXCEEDED, "同人同年同类型慰问已超出次数或金额上限"),
            Map.entry(UNION_RELIEF_STATE_CONFLICT, "慰问帮扶申请当前状态不允许该操作"),
            Map.entry(UNION_APPROVAL_UNAVAILABLE, "审批服务不可用，申请已保存为草稿"),
            Map.entry(UNION_APPROVAL_SYNC_FAILED, "审批状态同步失败，请稍后重试"),
            Map.entry(UNION_WELFARE_NOT_FOUND, "节日福利批次不存在或无权访问"),
            Map.entry(UNION_WELFARE_INVALID, "节日福利信息不合法，请检查名称、年度与标准金额"),
            Map.entry(UNION_WELFARE_STATE_CONFLICT, "福利批次当前状态不允许该操作"),
            Map.entry(UNION_WELFARE_ITEM_NOT_FOUND, "福利发放明细不存在"),
            Map.entry(UNION_WELFARE_ITEM_STATE_CONFLICT, "福利明细当前状态不允许该操作"),
            Map.entry(UNION_WELFARE_ITEM_FORBIDDEN, "只能签收本人的福利明细"),
            Map.entry(UNION_ACTIVITY_NOT_FOUND, "工会活动不存在或无权访问"),
            Map.entry(UNION_ACTIVITY_INVALID, "工会活动信息不合法，请检查名称、时间与名额"),
            Map.entry(UNION_ACTIVITY_STATE_CONFLICT, "工会活动当前状态不允许该操作"),
            Map.entry(UNION_SIGNUP_DUPLICATED, "已报名该活动，请勿重复报名"),
            Map.entry(UNION_SIGNUP_QUOTA_EXCEEDED, "活动名额已满"),
            Map.entry(UNION_SIGNUP_NOT_FOUND, "报名记录不存在"),
            Map.entry(UNION_PROPOSAL_NOT_FOUND, "职代会提案不存在或无权访问"),
            Map.entry(UNION_PROPOSAL_INVALID, "提案信息不合法，请检查标题与内容"),
            Map.entry(UNION_PROPOSAL_STATE_CONFLICT, "提案状态不允许该流转"),
            Map.entry(UNION_PROPOSAL_FORBIDDEN, "无权操作该提案"),
            Map.entry(METRIC_INVALID, "统计参数不合法，请检查统计日与统计范围"),
            Map.entry(METRIC_EXPORT_LIMIT, "导出区间超出上限，请缩小区间"),
            Map.entry(NOTICE_SCENE_INVALID, "通知场景不合法"),
            Map.entry(NOTICE_SEND_FAILED, "通知投递失败，请稍后重试"),
            Map.entry(EMPLOYEE_SYNC_FAILED, "ERP 员工目录拉取失败，已保留旧快照"),
            Map.entry(EMPLOYEE_SYNC_EMPTY, "ERP 员工目录为空，已拒绝覆盖旧快照"));

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private PartyErrorCode() {
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

    /**
     * 错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 提示语
     */
    private static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "党政工会业务校验失败");
    }
}
