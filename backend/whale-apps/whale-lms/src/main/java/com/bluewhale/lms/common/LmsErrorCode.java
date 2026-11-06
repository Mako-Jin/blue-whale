package com.bluewhale.lms.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 学习培训考试业务错误码。
 *
 * <p>段位约定：{@code 42xxx} 归 LMS；42000–42009 为门禁、身份与课程资源冲突
 * （跨租户一律按不存在处理），42010–42029 为培训计划（班级 / 场次 / 分配 / 签到），
 * 42030–42059 为题库 / 试卷 / 考试 / 判分，42060 起留给证书与后续上下文。</p>
 *
 * @author BlueWhale
 */
public final class LmsErrorCode {

    /** 需求门禁未通过，业务接口未开放 */
    public static final int GATE_CLOSED = 42000;

    /** 缺少可信操作人身份，写操作失败关闭 */
    public static final int ACTOR_REQUIRED = 42001;

    /** 课程不存在或无权访问 */
    public static final int COURSE_NOT_FOUND = 42002;

    /** 课程编码已存在 */
    public static final int COURSE_CODE_CONFLICT = 42003;

    /** 课程状态不允许该操作（含发布时无章节） */
    public static final int COURSE_STATE_CONFLICT = 42004;

    /** 课程未发布，不可报名 */
    public static final int COURSE_NOT_PUBLISHED = 42005;

    /** 课件或章节在发布态不可变更，需先归档下架 */
    public static final int COURSEWARE_STATE_CONFLICT = 42006;

    /** 学习记录不存在或无权访问（未报名先报进度） */
    public static final int ENROLLMENT_NOT_FOUND = 42007;

    /** 章节不属于该课程 */
    public static final int CHAPTER_NOT_IN_COURSE = 42008;

    /** 学习进度参数非法（超出 0–100） */
    public static final int PROGRESS_INVALID = 42009;

    /** 培训班不存在或无权访问 */
    public static final int TRAINING_NOT_FOUND = 42010;

    /** 培训班编码已存在 */
    public static final int TRAINING_CODE_CONFLICT = 42011;

    /** 培训班状态不允许该操作（含发布时无场次、已结课） */
    public static final int TRAINING_STATE_CONFLICT = 42012;

    /** 开班 / 结班日期非法 */
    public static final int TRAINING_PERIOD_INVALID = 42013;

    /** 场次不存在或不属于该培训班 */
    public static final int SESSION_NOT_FOUND = 42014;

    /** 场次时间或课时非法 */
    public static final int SESSION_TIME_INVALID = 42015;

    /** 必修分配名单为空 */
    public static final int ASSIGNMENT_EMPTY = 42016;

    /** 不在签到时间窗内 */
    public static final int CHECKIN_WINDOW_CLOSED = 42017;

    /** 未分配（未报名）该培训班，不可签到 */
    public static final int CHECKIN_NOT_ASSIGNED = 42018;

    /** 补签缺少操作人或备注 */
    public static final int CHECKIN_REMARK_REQUIRED = 42019;

    /** 分配人数超出计划人数 */
    public static final int TRAINING_CAPACITY_EXCEEDED = 42020;

    /** 题目不存在或无权访问 */
    public static final int QUESTION_NOT_FOUND = 42030;

    /** 题目编码已存在 */
    public static final int QUESTION_CODE_CONFLICT = 42031;

    /** 题目内容不合法（题干 / 选项 / 答案 / 分值） */
    public static final int QUESTION_INVALID = 42032;

    /** 题目状态不允许该操作（停用不可编辑、已被试卷引用不可删） */
    public static final int QUESTION_STATE_CONFLICT = 42033;

    /** 试卷不存在或无权访问 */
    public static final int PAPER_NOT_FOUND = 42034;

    /** 试卷编码已存在 */
    public static final int PAPER_CODE_CONFLICT = 42035;

    /** 试卷状态不允许该操作（发布冻结 / 非草稿不可改题） */
    public static final int PAPER_STATE_CONFLICT = 42036;

    /** 试卷参数不合法（名称 / 时长 / 及格分） */
    public static final int PAPER_INVALID = 42037;

    /** 抽题规则不合法 */
    public static final int PAPER_RULE_INVALID = 42038;

    /** 抽题候选不足（不静默补齐，直接失败） */
    public static final int PAPER_RULE_INSUFFICIENT = 42039;

    /** 考试不存在或无权访问 */
    public static final int EXAM_NOT_FOUND = 42040;

    /** 考试编码已存在 */
    public static final int EXAM_CODE_CONFLICT = 42041;

    /** 考试状态不允许该操作 */
    public static final int EXAM_STATE_CONFLICT = 42042;

    /** 不在考试时间窗内 */
    public static final int EXAM_WINDOW_CLOSED = 42043;

    /** 作答次数已用尽 */
    public static final int EXAM_ATTEMPT_LIMIT = 42044;

    /** 作答记录不存在或无权访问 */
    public static final int ATTEMPT_NOT_FOUND = 42045;

    /** 作答状态不允许该操作（已交卷） */
    public static final int ATTEMPT_STATE_CONFLICT = 42046;

    /** 已超过本次作答截止时间 */
    public static final int ATTEMPT_EXPIRED = 42047;

    /** 答案不合法（题目不属于本卷 / 格式非法） */
    public static final int ANSWER_INVALID = 42048;

    /** 该题已判分，不可重复判 */
    public static final int GRADE_NOT_PENDING = 42049;

    /** 判分越界（超出本题分值或为负） */
    public static final int GRADE_INVALID = 42050;

    /** 考试 / 试卷参数不合法（时间窗、时长、次数） */
    public static final int EXAM_INVALID = 42051;

    /** 错误码默认提示语表（List 级 switch 会顶到圈复杂度上限，统一走查表） */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(GATE_CLOSED, "需求门禁未通过，学习功能未开放"),
            Map.entry(ACTOR_REQUIRED, "缺少可信操作人身份"),
            Map.entry(COURSE_NOT_FOUND, "课程不存在或无权访问"),
            Map.entry(COURSE_CODE_CONFLICT, "课程编码已存在"),
            Map.entry(COURSE_STATE_CONFLICT, "课程状态不允许该操作"),
            Map.entry(COURSE_NOT_PUBLISHED, "课程未发布，不可报名"),
            Map.entry(COURSEWARE_STATE_CONFLICT, "发布态课程的内容不可变更，请先归档下架"),
            Map.entry(ENROLLMENT_NOT_FOUND, "学习记录不存在，请先报名"),
            Map.entry(CHAPTER_NOT_IN_COURSE, "章节不属于该课程"),
            Map.entry(PROGRESS_INVALID, "学习进度参数非法"),
            Map.entry(TRAINING_NOT_FOUND, "培训班不存在或无权访问"),
            Map.entry(TRAINING_CODE_CONFLICT, "培训班编码已存在"),
            Map.entry(TRAINING_STATE_CONFLICT, "培训班状态不允许该操作"),
            Map.entry(TRAINING_PERIOD_INVALID, "开班或结班日期非法"),
            Map.entry(SESSION_NOT_FOUND, "场次不存在或不属于该培训班"),
            Map.entry(SESSION_TIME_INVALID, "场次时间或课时非法"),
            Map.entry(ASSIGNMENT_EMPTY, "必修分配名单不能为空"),
            Map.entry(CHECKIN_WINDOW_CLOSED, "当前不在签到时间窗内"),
            Map.entry(CHECKIN_NOT_ASSIGNED, "未分配该培训班，不可签到"),
            Map.entry(CHECKIN_REMARK_REQUIRED, "补签必须记录操作人与备注"),
            Map.entry(TRAINING_CAPACITY_EXCEEDED, "分配人数超出计划人数"),
            Map.entry(QUESTION_NOT_FOUND, "题目不存在或无权访问"),
            Map.entry(QUESTION_CODE_CONFLICT, "题目编码已存在"),
            Map.entry(QUESTION_INVALID, "题目内容不合法"),
            Map.entry(QUESTION_STATE_CONFLICT, "题目状态不允许该操作"),
            Map.entry(PAPER_NOT_FOUND, "试卷不存在或无权访问"),
            Map.entry(PAPER_CODE_CONFLICT, "试卷编码已存在"),
            Map.entry(PAPER_STATE_CONFLICT, "试卷状态不允许该操作"),
            Map.entry(PAPER_INVALID, "试卷参数不合法"),
            Map.entry(PAPER_RULE_INVALID, "抽题规则不合法"),
            Map.entry(PAPER_RULE_INSUFFICIENT, "抽题候选不足，请放宽条件或减少题量"),
            Map.entry(EXAM_NOT_FOUND, "考试不存在或无权访问"),
            Map.entry(EXAM_CODE_CONFLICT, "考试编码已存在"),
            Map.entry(EXAM_STATE_CONFLICT, "考试状态不允许该操作"),
            Map.entry(EXAM_WINDOW_CLOSED, "当前不在考试时间窗内"),
            Map.entry(EXAM_ATTEMPT_LIMIT, "作答次数已用尽"),
            Map.entry(ATTEMPT_NOT_FOUND, "作答记录不存在或无权访问"),
            Map.entry(ATTEMPT_STATE_CONFLICT, "作答状态不允许该操作"),
            Map.entry(ATTEMPT_EXPIRED, "已超过本次作答截止时间"),
            Map.entry(ANSWER_INVALID, "答案不合法"),
            Map.entry(GRADE_NOT_PENDING, "该题无需判分或已判分"),
            Map.entry(GRADE_INVALID, "判分超出本题分值范围"),
            Map.entry(EXAM_INVALID, "考试参数不合法"));

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private LmsErrorCode() {
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
        return MESSAGES.getOrDefault(code, "学习培训考试业务校验失败");
    }
}
