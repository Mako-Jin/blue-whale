package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

/**
 * 批量触达任务分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class MarketingSendQuery {

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 关键字：任务号 / 主题 */
    private String keyword;

    /** 营销活动过滤 */
    private Long campaignId;

    /** 状态过滤 */
    private String status;

    /** 渠道过滤 */
    private String channel;

    /** 数据范围用户（非全量数据范围时按负责人过滤） */
    private Long scopeUserId;
}
