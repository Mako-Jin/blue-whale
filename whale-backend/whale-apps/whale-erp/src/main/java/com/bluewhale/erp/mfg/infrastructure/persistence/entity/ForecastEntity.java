package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 需求预测批次实体，对应表 {@code erp_mfg_forecast}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_forecast")
public class ForecastEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 预测批次号 */
    private String batchNo;

    /** 预测期间 */
    private String period;

    /** 版本号 */
    private Integer version;

    /** 状态 */
    private String status;

    /** 预测总数量 */
    private BigDecimal totalQuantity;

    /** 备注 */
    private String remark;
}
