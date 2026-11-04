package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 主生产计划实体，对应表 {@code erp_mfg_mps}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mps")
public class MpsEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 计划单号 */
    private String planNo;

    /** 计划期间 */
    private String period;

    /** 状态 */
    private String status;

    /** 计划总数量 */
    private BigDecimal totalPlanned;

    /** 备注 */
    private String remark;
}
