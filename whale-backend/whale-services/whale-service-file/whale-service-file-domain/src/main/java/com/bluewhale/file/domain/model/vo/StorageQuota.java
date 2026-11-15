package com.bluewhale.file.domain.model.vo;

/**
 * 存储配额值对象：一次写入需要同时满足的个人、租户与桶三个维度的上限。
 *
 * <p>任一维度 {@code <=0} 表示该维度不限；租户维度只在存在租户上下文时生效，
 * 无租户态的内网调用由仓储侧跳过租户校验，避免全局统计误伤。</p>
 *
 * @param userBytes 单用户配额字节数；{@code <=0} 表示不限
 * @param tenantBytes 租户配额字节数；{@code <=0} 表示不限
 * @param bucketBytes 单桶配额字节数（租户内计量）；{@code <=0} 表示不限
 * @author BlueWhale
 */
public record StorageQuota(long userBytes, long tenantBytes, long bucketBytes) {

    /**
     * 判断是否配置了任一维度的上限
     *
     * @return 三个维度均不限时返回 {@code false}
     */
    public boolean hasAnyLimit() {
        return userBytes > 0 || tenantBytes > 0 || bucketBytes > 0;
    }

    /**
     * 判断个人维度是否启用
     *
     * @return 个人配额为正数时返回 {@code true}
     */
    public boolean userLimited() {
        return userBytes > 0;
    }

    /**
     * 判断租户维度是否启用
     *
     * @return 租户配额为正数时返回 {@code true}
     */
    public boolean tenantLimited() {
        return tenantBytes > 0;
    }

    /**
     * 判断桶维度是否启用
     *
     * @return 桶配额为正数时返回 {@code true}
     */
    public boolean bucketLimited() {
        return bucketBytes > 0;
    }
}
