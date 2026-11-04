package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

/**
 * 客户分页查询条件。
 *
 * @author BlueWhale
 */
@Data
public class CustomerQuery extends com.bluewhale.common.core.query.PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字（编码 / 名称 / 简称） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 等级 */
    private String level;

    /** 来源 */
    private String source;

    /** 行业 */
    private String industry;

    /** 负责人（数据范围过滤） */
    private Long ownerId;

    /** 公海状态 */
    private String poolStatus;

    /** 创建时间起 */
    private java.time.LocalDateTime createdFrom;

    /** 创建时间止 */
    private java.time.LocalDateTime createdTo;
}
