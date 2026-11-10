package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频分类实体，对应表 {@code vd_category}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_category")
public class CategoryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 父分类 ID */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 分类编码 */
    private String code;

    /** 适用范围 */
    private String contentTypeScope;

    /** 排序 */
    private Integer sortNo;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
