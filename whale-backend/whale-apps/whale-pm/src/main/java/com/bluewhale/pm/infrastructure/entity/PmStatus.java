package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作项状态实体，对应表 {@code pm_status}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_status")
public class PmStatus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 状态编码（项目内唯一） */
    private String code;

    /** 状态名称 */
    private String name;

    /** 状态分类：TODO / IN_PROGRESS / DONE */
    private String category;

    /** 排序号 */
    private Integer sortNo;

    /** 是否默认状态：1 是 / 0 否 */
    private Integer isDefault;
}
