package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 类目实体，对应表 {@code mall_category}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_category")
public class CategoryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上级类目 ID，0 为一级 */
    private Long parentId;

    /** 类目名称 */
    private String categoryName;

    /** 层级 */
    private Integer categoryLevel;

    /** 排序号 */
    private Integer sortNo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
