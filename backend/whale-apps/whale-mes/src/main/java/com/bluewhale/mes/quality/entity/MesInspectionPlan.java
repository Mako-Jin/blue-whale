package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检验计划实体，对应表 {@code mes_qc_inspection_plan}。
 *
 * <p>首件检验与 IPQC 巡检共用计划模型；计划项由 {@link MesInspectionPlanItem} 承载。
 * 检验类型决定任务语义：{@code FIRST_ARTICLE} 未通过时阻断批量释放，{@code IPQC} 按频次巡检。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_inspection_plan")
public class MesInspectionPlan extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 计划编号（租户内唯一） */
    private String planNo;

    /** 计划名称 */
    private String planName;

    /** 检验类型：FIRST_ARTICLE / IPQC */
    private String inspectionType;

    /** 适用物料编码 */
    private String itemCode;

    /** 适用工序编码 */
    private String operationCode;

    /** 适用产线 ID */
    private Long lineId;

    /** 适用产线编码快照 */
    private String lineCode;

    /** 抽样数量 */
    private Integer sampleSize;

    /** 巡检频次（分钟） */
    private Integer frequencyMinutes;

    /** 检验 FAILED 是否自动生成 NCR：0 否 / 1 是 */
    private Integer autoNcr;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
