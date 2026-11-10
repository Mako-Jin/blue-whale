package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品实体，对应表 {@code mall_spu}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_spu")
public class SpuEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 商品编码 */
    private String spuCode;

    /** 商品名称 */
    private String spuName;

    /** 所属类目 ID */
    private Long categoryId;

    /** 品牌 */
    private String brand;

    /** 主图 */
    private String mainImage;

    /** 轮播图 JSON 数组文本 */
    private String images;

    /** 图文详情 */
    private String detail;

    /** 状态 */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;
}
