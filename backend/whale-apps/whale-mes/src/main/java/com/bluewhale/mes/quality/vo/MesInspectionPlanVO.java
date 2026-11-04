package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验计划视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionPlanVO {

    /** 计划 ID */
    private Long id;

    /** 计划编号 */
    private String planNo;

    /** 计划名称 */
    private String planName;

    /** 检验类型 */
    private String inspectionType;

    /** 适用物料编码 */
    private String itemCode;

    /** 适用工序编码 */
    private String operationCode;

    /** 适用产线 ID */
    private Long lineId;

    /** 适用产线编码 */
    private String lineCode;

    /** 抽样数量 */
    private Integer sampleSize;

    /** 巡检频次（分钟） */
    private Integer frequencyMinutes;

    /** 自动生成 NCR 标志 */
    private Integer autoNcr;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 检验项列表 */
    private List<MesInspectionPlanItemVO> items;
}
