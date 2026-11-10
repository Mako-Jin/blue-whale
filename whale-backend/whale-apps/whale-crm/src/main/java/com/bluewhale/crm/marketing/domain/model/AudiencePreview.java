package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 人群预览结果（命中数量 + 前若干样例）。
 *
 * @author BlueWhale
 */
@Data
public class AudiencePreview {

    /** 命中数量 */
    private Long count;

    /** 样例客户（最多 20 条） */
    private List<Sample> samples;

    /**
     * 预览样例客户。
     *
     * @param customerId 客户 ID
     * @param customerName 客户名称
     * @param level 客户等级
     * @param industry 行业
     * @param source 来源
     */
    public record Sample(Long customerId, String customerName, String level, String industry, String source) {
    }
}
