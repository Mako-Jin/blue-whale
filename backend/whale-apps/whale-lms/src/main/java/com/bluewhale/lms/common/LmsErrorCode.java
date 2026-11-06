package com.bluewhale.lms.common;

import com.bluewhale.common.core.exception.BizException;

/**
 * 学习培训考试业务错误码。
 *
 * <p>段位约定：{@code 42xxx} 归 LMS；42000–42009 为门禁、身份与课程资源冲突
 * （跨租户一律按不存在处理），42010 起为学习进度与后续上下文。</p>
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
        return switch (code) {
            case GATE_CLOSED -> "需求门禁未通过，学习功能未开放";
            case ACTOR_REQUIRED -> "缺少可信操作人身份";
            case COURSE_NOT_FOUND -> "课程不存在或无权访问";
            case COURSE_CODE_CONFLICT -> "课程编码已存在";
            case COURSE_STATE_CONFLICT -> "课程状态不允许该操作";
            case COURSE_NOT_PUBLISHED -> "课程未发布，不可报名";
            case COURSEWARE_STATE_CONFLICT -> "发布态课程的内容不可变更，请先归档下架";
            case ENROLLMENT_NOT_FOUND -> "学习记录不存在，请先报名";
            case CHAPTER_NOT_IN_COURSE -> "章节不属于该课程";
            case PROGRESS_INVALID -> "学习进度参数非法";
            default -> "学习培训考试业务校验失败";
        };
    }
}
