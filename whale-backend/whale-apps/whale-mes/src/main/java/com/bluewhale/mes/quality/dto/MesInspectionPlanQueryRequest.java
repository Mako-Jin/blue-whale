package com.bluewhale.mes.quality.dto;

import lombok.Data;

/**
 * 检验计划分页查询条件
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionPlanQueryRequest {

    /** 计划编号（模糊） */
    private String planNo;

    /** 计划名称（模糊） */
    private String planName;

    /** 检验类型 */
    private String inspectionType;

    /** 状态 */
    private String status;

    /** 当前页 */
    private Long current;

    /** 每页条数 */
    private Long size;
}
