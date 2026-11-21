package com.bluewhale.form.po.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按天聚合的提交条数（考功殿概览 T5.7）。
 *
 * @author BlueWhale
 */
@Data
public class FormDaySubmitPo {

    /** 日期（{@code DATE(create_time)} 的输出，仅用于分组标签） */
    private LocalDate day;

    /** 当日提交条数 */
    private Long submits;
}
