package com.bluewhale.pdm.part.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDM 物料分类
 *
 * <p>树形分类，{@code code} 租户内唯一；零件通过 {@code category_code} 逻辑引用。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_part_category")
public class PdmPartCategory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上级分类ID，0 表示根 */
    private Long parentId;

    /** 分类编码（租户内唯一） */
    private String code;

    /** 分类名称 */
    private String name;

    /** 分类路径，如 /1/2/ */
    private String path;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;
}
