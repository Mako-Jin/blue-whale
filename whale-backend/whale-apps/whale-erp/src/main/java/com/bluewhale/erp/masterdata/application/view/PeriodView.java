package com.bluewhale.erp.masterdata.application.view;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计期间视图。
 *
 * @author BlueWhale
 */
@Data
public class PeriodView {

    /** 主键 */
    private Long id;

    /** 期间编码 */
    private String periodCode;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态 */
    private String status;

    /** 关账时间 */
    private LocalDateTime closedAt;

    /** 关账人 */
    private Long closedBy;

    /** 备注 */
    private String remark;
}
