package com.bluewhale.crm.sales.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索活动记录（append-only）。
 *
 * @author BlueWhale
 */
@Data
public class LeadActivity {

    /** 主键 */
    private Long id;

    /** 线索 ID */
    private Long leadId;

    /** 渠道 */
    private String channel;

    /** 内容 */
    private String content;

    /** 结果 */
    private String result;

    /** 下次行动时间 */
    private LocalDateTime nextActionAt;

    /** 记录人 */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
