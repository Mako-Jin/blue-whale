package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报工不良明细实体，对应表 {@code mes_ex_report_defect}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_report_defect")
public class MesReportDefect extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 报工记录 ID */
    private Long reportId;

    /** 缺陷编码 */
    private String defectCode;

    /** 缺陷名称快照 */
    private String defectName;

    /** 不良数量 */
    private BigDecimal quantity;
}
