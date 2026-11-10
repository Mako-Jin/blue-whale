package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

/**
 * 渠道伙伴分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class PartnerQuery {

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 关键字：伙伴编码 / 名称 */
    private String keyword;

    /** 伙伴类型过滤 */
    private String partnerType;

    /** 状态过滤 */
    private String status;

    /** 区域过滤 */
    private String region;

    /** 数据范围用户（非全量数据范围时按负责人过滤） */
    private Long scopeUserId;
}
