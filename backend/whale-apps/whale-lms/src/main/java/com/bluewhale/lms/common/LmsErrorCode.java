package com.bluewhale.lms.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 学习培训考试业务错误码。
 *
 * <p>段位约定：{@code 42xxx} 归 LMS；42000–42009 为门禁、身份与课程资源冲突
 * （跨租户一律按不存在处理），42010–42029 为培训计划（班级 / 场次 / 分配 / 签到），
 * 42030 起为考试、证书与后续上下文。</p>
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
            Map.entry(TRAINING_CAPACITY_EXCEEDED, "分配人数超出计划人数"));

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
