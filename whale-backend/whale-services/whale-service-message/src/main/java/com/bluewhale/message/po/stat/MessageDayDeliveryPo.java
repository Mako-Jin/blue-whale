package com.bluewhale.message.po.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按天聚合的投递条数（通政殿概览 T5.8）。
 *
 * @author BlueWhale
 */
@Data
public class MessageDayDeliveryPo {

    /** 日期（投递时间所在日） */
    private LocalDate day;

    /** 当日投递条数 */
    private Long deliveries;
}
