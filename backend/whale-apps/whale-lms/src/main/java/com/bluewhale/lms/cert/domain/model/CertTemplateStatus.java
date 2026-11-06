package com.bluewhale.lms.cert.domain.model;

/**
 * 证书模板状态。
 *
 * @author BlueWhale
 */
public enum CertTemplateStatus {

    /** 启用：可用于发证 */
    ENABLED,

    /** 停用：仅保留历史，不可用于新的发证 */
    DISABLED;

    /**
     * 解析状态（未知值按 {@link #DISABLED} 处理）。
     *
     * <p>这里刻意与 {@link CertStatus} 的默认值不同：证书状态未知时按有效处理是为了让历史
     * 数据仍可展示；模板状态未知时按停用处理，因为模板决定「能不能发证」，
     * 宁可直接拦住也不能凭一个坏值把证发出去。</p>
     *
     * @param value 原始值
     * @return 状态
     */
    public static CertTemplateStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return DISABLED;
        }
        for (CertTemplateStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return DISABLED;
    }

    /**
     * 是否可用于发证。
     *
     * @return 启用返回 true
     */
    public boolean canIssue() {
        return this == ENABLED;
    }
}
