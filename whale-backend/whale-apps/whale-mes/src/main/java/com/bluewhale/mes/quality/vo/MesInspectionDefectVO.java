package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验不良视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionDefectVO {

    /** 不良记录 ID */
    private Long id;

    /** 检验任务 ID */
    private Long taskId;

    /** 缺陷代码 */
    private String defectCode;

    /** 缺陷名称 */
    private String defectName;

    /** 不良数量 */
    private BigDecimal quantity;

    /** 严重度 */
    private String severity;

    /** 不良描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;
}
