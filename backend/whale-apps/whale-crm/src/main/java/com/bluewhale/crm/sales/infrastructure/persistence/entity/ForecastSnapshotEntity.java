package com.bluewhale.crm.sales.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售预测快照实体，对应表 {@code crm_forecast_snapshot}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_forecast_snapshot")
public class ForecastSnapshotEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 周期 */
    private String period;

    /** 周期类型 */
    private String periodType;

    /** 范围类型 */
    private String scopeType;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 管道金额 */
    private BigDecimal pipelineAmount;

    /** 最佳金额 */
    private BigDecimal bestAmount;

    /** 承诺金额 */
    private BigDecimal commitAmount;

    /** 加权金额 */
    private BigDecimal weightedAmount;

    /** 未关闭商机数 */
    private Integer opportunityCount;

    /** 是否手工调整 */
    private Integer manualAdjusted;

    /** 备注 */
    private String remark;

    /** 生成人 */
    private Long createdBy;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
