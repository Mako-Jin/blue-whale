package com.bluewhale.mes.workforce.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 交接班分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesHandoverQueryRequest {

    /** 交接单号（模糊） */
    private String handoverNo;

    /** 班组 ID */
    private Long teamId;

    /** 交接日期 */
    private LocalDate handoverDate;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
