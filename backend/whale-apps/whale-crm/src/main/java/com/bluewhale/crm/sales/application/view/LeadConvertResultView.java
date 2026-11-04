package com.bluewhale.crm.sales.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索转化结果视图。
 *
 * @author BlueWhale
 */
@Data
public class LeadConvertResultView {

    /** 线索 ID */
    private Long leadId;

    /** 客户 ID */
    private Long customerId;

    /** 商机 ID */
    private Long opportunityId;

    /** 转化时间 */
    private LocalDateTime convertedAt;

    /** 是否为重复转化（true 表示返回既有结果，未新建） */
    private boolean alreadyConverted;
}
