package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检验不良实体，对应表 {@code mes_qc_inspection_defect}。
 *
 * <p>记录检验任务内发现的缺陷代码与数量，作为 NCR 生成的缺陷来源。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_inspection_defect")
public class MesInspectionDefect extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 检验任务 ID */
    private Long taskId;

    /** 缺陷代码 */
    private String defectCode;

    /** 缺陷名称快照 */
    private String defectName;

    /** 不良数量 */
    private BigDecimal quantity;

    /** 严重度：MINOR / MAJOR / CRITICAL */
    private String severity;

    /** 不良描述 */
    private String description;
}
