package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 规格实体，对应表 {@code mall_sku}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_sku")
public class SkuEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属商品 ID */
    private Long spuId;

    /** SKU 编码 */
    private String skuCode;

    /** 规格描述 JSON */
    private String specJson;

    /** SKU 图 */
    private String imageUrl;

    /** 单件重量（kg，按重运费用） */
    private BigDecimal weightKg;

    /** 状态 */
    private String status;

    /** 排序号 */
    private Integer sortNo;
}
