package com.bluewhale.crm.marketing.domain.model;

import lombok.Data;

/**
 * 渠道报单分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class PartnerReportQuery {

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 关键字：报单号 / 客户名称 / 项目名称 */
    private String keyword;

    /** 渠道伙伴过滤 */
    private Long partnerId;

    /** 客户过滤 */
    private Long customerId;

    /** 状态过滤 */
    private String status;

    /** 数据范围用户（非全量数据范围时按负责人过滤） */
    private Long scopeUserId;
}
