package com.bluewhale.lms.cert.domain.model;

/**
 * 证书来源类型 / 发证依据。
 *
 * <p>模板的「发证依据」与证书的「来源类型」取值一致：模板用它声明发证前要校验什么，
 * 证书用它在快照里记录实际依据。除 {@link #MANUAL} 外都必须给出对应的来源业务 ID，
 * 否则发证时可以绕过前置条件。</p>
 *
 * @author BlueWhale
 */
public enum CertSource {

    /** 手工发证：由管理员直接发放，不校验业务来源 */
    MANUAL,

    /** 课程完成发证 */
    COURSE,

    /** 考试通过发证 */
    EXAM,

    /** 培训完成发证 */
    TRAINING;

    /**
     * 解析来源类型（大小写不敏感，未知或空按 {@link #MANUAL} 处理）。
     *
     * @param value 原始值
     * @return 来源类型
     */
    public static CertSource parse(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        for (CertSource source : values()) {
            if (source.name().equalsIgnoreCase(value.trim())) {
                return source;
            }
        }
        return MANUAL;
    }

    /**
     * 是否需要校验业务来源。
     *
     * @return 非手工发证返回 true
     */
    public boolean requiresVerification() {
        return this != MANUAL;
    }
}
