package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 看板列实体，对应表 {@code pm_board_column}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_board_column")
public class PmBoardColumn extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 看板ID */
    private Long boardId;

    /** 映射状态ID */
    private Long statusId;

    /** 列名称 */
    private String name;

    /** 排序号（升序） */
    private Integer sortNo;

    /** WIP 上限（0 表示不限） */
    private Integer wipLimit;
}
