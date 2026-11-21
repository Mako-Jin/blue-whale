package com.bluewhale.audit.mapper.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按天聚合的审计记录条数（起居殿概览 T5.9）。
 *
 * @author BlueWhale
 */
@Data
public class AuditDayCountPo {

    /** 日期（审计记录的 create_time 所在日） */
    private LocalDate day;

    /** 当日审计条数 */
    private Long audits;
}
