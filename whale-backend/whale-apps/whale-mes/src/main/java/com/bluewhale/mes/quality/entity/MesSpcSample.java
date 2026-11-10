package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 样本实体，对应表 {@code mes_qc_spc_sample}。
 *
 * <p>子组内逐点录入；子组满额后回填均值与极差，若命中控制限规则，同一子组样本统一标记
 * {@code alert_flag=1} 并写入 {@code alert_rule}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_spc_sample")
public class MesSpcSample extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 样本号（租户内唯一） */
    private String sampleNo;

    /** SPC 规则 ID */
    private Long ruleId;

    /** 规则编码快照 */
    private String ruleCode;

    /** 检验计划项 ID */
    private Long planItemId;

    /** 来源检验任务 ID */
    private Long inspectionTaskId;

    /** 工序编码 */
    private String operationCode;

    /** 子组号 */
    private Integer subgroupNo;

    /** 子组内样本序号 */
    private Integer sampleSeq;

    /** 子组大小快照 */
    private Integer subgroupSize;

    /** 实测值 */
    private BigDecimal measuredValue;

    /** 子组均值 */
    private BigDecimal subgroupXbar;

    /** 子组极差 */
    private BigDecimal subgroupRange;

    /** 是否命中规则：0 否 / 1 是 */
    private Integer alertFlag;

    /** 命中规则说明 */
    private String alertRule;

    /** 采样时间 */
    private LocalDateTime sampledAt;
}
