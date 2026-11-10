package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 条码规则实体，对应表 {@code mes_tr_barcode_rule}。
 *
 * <p>规则决定条码前缀、码制（Code128 / QR）与流水号长度；停用规则不允许再生成条码。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_barcode_rule")
public class MesBarcodeRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 规则编码（租户内唯一） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 条码前缀 */
    private String prefix;

    /** 码制：CODE128 / QR */
    private String codeType;

    /** 流水号长度（4–12） */
    private Integer serialLength;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
