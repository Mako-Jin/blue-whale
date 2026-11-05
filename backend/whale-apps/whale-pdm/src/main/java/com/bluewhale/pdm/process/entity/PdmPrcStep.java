package com.bluewhale.pdm.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * PDM 工艺工序
 *
 * <p>同一路线内工序序号唯一；标准工时与准备工时不得为负。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_prc_step")
public class PdmPrcStep extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工艺路线ID */
    private Long routeId;

    /** 工序序号（路线内唯一） */
    private Integer stepNo;

    /** 工序名称 */
    private String name;

    /** 工作中心 */
    private String workCenter;

    /** 标准工时（小时） */
    private BigDecimal standardHours;

    /** 准备工时（小时） */
    private BigDecimal setupHours;

    /** 工序说明 */
    private String description;
}
