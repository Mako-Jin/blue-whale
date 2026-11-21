package com.bluewhale.file.po.stat;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按天新增聚合行（藏书殿概览 T5.4）。
 *
 * @author BlueWhale
 */
@Data
public class FileDayCountPo {

    /** 日期（{@code DATE(create_time)}） */
    private LocalDate day;

    /** 当日新增根文件数 */
    private Long files;
}
