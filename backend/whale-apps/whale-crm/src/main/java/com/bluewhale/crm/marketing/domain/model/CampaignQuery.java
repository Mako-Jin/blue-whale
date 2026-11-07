package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

/**
 * 营销活动分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class CampaignQuery {

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 关键字：活动编码 / 名称 */
    private String keyword;

    /** 状态过滤 */
    private String status;

    /** 类型过滤 */
    private String campaignType;

    /** 渠道过滤 */
    private String channel;

    /** 负责人过滤 */
    private Long ownerId;

    /** 归属部门过滤 */
    private Long deptId;

    /** 数据范围用户（非全量数据范围时按负责人过滤） */
    private Long scopeUserId;
}
