package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 出差行程视图。
 *
 * @author BlueWhale
 */
@Data
public class TripItineraryView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 行程序号 */
    private Integer dayNo;

    /** 行程日期 */
    private LocalDate tripDate;

    /** 出发地 */
    private String fromPlace;

    /** 到达地 */
    private String toPlace;

    /** 交通方式 */
    private String transport;

    /** 备注 */
    private String remark;
}
