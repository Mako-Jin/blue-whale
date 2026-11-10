package com.bluewhale.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPC 规则实体，对应表 {@code mes_qc_spc_rule}。
 *
 * <p>默认 Xbar-R 控制图；规则绑定检验计划项，样本录入时必须校验该检验项有效。
 * 子组满额后按控制限判定，命中写入样本 {@code alert_flag} 与 {@code alert_rule} 留痕。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_qc_spc_rule")
public class MesSpcRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码（租户内唯一） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 关联检验计划项 ID */
    private Long planItemId;

    /** 工序编码快照 */
    private String operationCode;

    /** 控制图类型：XBAR_R */
    private String chartType;

    /** 子组大小 */
    private Integer subgroupSize;

    /** 中心线 CL */
    private BigDecimal centerLine;

    /** 均值上控制限 UCL */
    private BigDecimal ucl;

    /** 均值下控制限 LCL */
    private BigDecimal lcl;

    /** 极差上控制限 */
    private BigDecimal rUcl;

    /** 极差下控制限 */
    private BigDecimal rLcl;

    /** 命中处理：WARN / BLOCK */
    private String alertAction;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
