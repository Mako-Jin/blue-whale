package com.bluewhale.party.analytics.domain.model;

/**
 * 统计快照范围。
 *
 * <p>{@link #ALL} 只有一行（scope_id 固定 0），支部维度按支部各一行；
 * 不提供「按上级支部汇总下级」——汇总口径会随组织架构调整而变，
 * 需要时在看板层把下级支部的快照相加，历史快照仍保持当时的支部边界。</p>
 *
 * @author BlueWhale
 */
public enum MetricScope {

    /** 全租户 */
    ALL,

    /** 单个支部 */
    BRANCH;

    /**
     * 解析范围编码（大小写不敏感，空值按 ALL）。
     *
     * @param code 范围编码
     * @return 范围
     */
    public static MetricScope parse(String code) {
        if (code == null || code.isBlank()) {
            return ALL;
        }
        return "BRANCH".equalsIgnoreCase(code.trim()) ? BRANCH : ALL;
    }
}
