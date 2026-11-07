package com.bluewhale.oa.analytics.application.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 报表查询条件。
 *
 * <p>{@code from/to} 为统计日期区间；{@code year} 仅假期余额报表使用，
 * 未传时取 {@code from} 所在年份。缺省期间由应用层补齐为本月。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReportQuery {

    /** 起始日期（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 截止日期（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** 额度年份（假期余额报表） */
    private Integer year;
}
