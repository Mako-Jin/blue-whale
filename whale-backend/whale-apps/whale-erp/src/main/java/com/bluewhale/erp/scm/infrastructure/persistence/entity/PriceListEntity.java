package com.bluewhale.erp.scm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 价目表实体，对应表 {@code erp_scm_price_list}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_scm_price_list")
public class PriceListEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 价格类型 */
    private String priceType;

    /** 往来方主键 */
    private Long partnerId;

    /** 往来方名称快照 */
    private String partnerName;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 单价 */
    private BigDecimal price;

    /** 币种 */
    private String currency;

    /** 有效期起 */
    private LocalDate validFrom;

    /** 有效期止 */
    private LocalDate validTo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
