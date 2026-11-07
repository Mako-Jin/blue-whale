package com.bluewhale.contract.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 合同分页查询请求
 *
 * @author BlueWhale
 */
@Data
public class ContractQueryRequest {

    /** 页码，从 1 起 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 10L;

    /** 合同编号（模糊） */
    private String code;

    /** 合同标题（模糊） */
    private String title;

    /** 合同类别（等值） */
    private String category;

    /** 状态（等值） */
    private String status;

    /** 金额下限（含） */
    private BigDecimal amountMin;

    /** 金额上限（含） */
    private BigDecimal amountMax;

    /** 仅看 30 天内到期（EFFECTIVE / SIGNED / APPROVED） */
    private Boolean expiringSoon;

    /** 弱关联业务类型（等值，如 CRM_CUSTOMER） */
    private String bizType;

    /** 弱关联业务 ID（等值） */
    private String bizId;
}
