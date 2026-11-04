package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 扫码防错规则实体，对应表 {@code mes_tr_scan_rule}。
 *
 * <p>按工位 / 工序匹配期望物料；命中差异时按 {@code action} 阻断或告警。
 * 工位与工序为空表示该维度不限，二者都为空表示仅校验条码有效性。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_scan_rule")
public class MesScanRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码（租户内唯一） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 适用工位编码（空表示不限） */
    private String stationCode;

    /** 适用工序编码（空表示不限） */
    private String operationCode;

    /** 期望物料编码（空表示仅做条码有效性校验） */
    private String expectedItemCode;

    /** 命中处理：BLOCK 阻断 / WARN 告警 */
    private String action;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
