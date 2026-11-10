package com.bluewhale.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 备件主档实体，对应表 {@code mes_eq_spare_part}。
 *
 * <p>备件金额与采购归 ERP；MES 只维护安全库存与领用流水。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_eq_spare_part")
public class MesSparePart extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 备件编码（租户内唯一） */
    private String partCode;

    /** 备件名称 */
    private String partName;

    /** 规格型号 */
    private String spec;

    /** 计量单位 */
    private String uom;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
