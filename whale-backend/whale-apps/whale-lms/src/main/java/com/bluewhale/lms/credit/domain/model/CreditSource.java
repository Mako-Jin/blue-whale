package com.bluewhale.lms.credit.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;

/**
 * 学分来源类型。
 *
 * <p>与 {@code CertSource} 刻意分开：证书允许「手工发放」，学分不允许手工凭空产生——
 * 学分必须是某件业务事实的折算结果，否则台账就失去可核对性。</p>
 *
 * @author BlueWhale
 */
public enum CreditSource {

    /** 课程完成 */
    COURSE,

    /** 考试通过 */
    EXAM,

    /** 培训完成 */
    TRAINING;

    /**
     * 解析来源类型（无法识别返回 null，由调用方决定如何报错）。
     *
     * @param value 原始值
     * @return 来源类型，无法识别时为 null
     */
    public static CreditSource parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (CreditSource source : values()) {
            if (source.name().equalsIgnoreCase(value.trim())) {
                return source;
            }
        }
        return null;
    }

    /**
     * 解析来源类型，无法识别时抛出参数错误。
     *
     * @param value 原始值
     * @return 来源类型
     */
    public static CreditSource require(String value) {
        CreditSource source = parse(value);
        if (source == null) {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_INVALID,
                    "学分来源只能是 COURSE / EXAM / TRAINING");
        }
        return source;
    }
}
