package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板列视图对象（ID 一律字符串）
 *
 * @author BlueWhale
 */
@Data
public class BoardColumnVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 看板ID */
    private String boardId;

    /** 映射状态ID */
    private String statusId;

    /** 状态编码 */
    private String statusCode;

    /** 列名称 */
    private String name;

    /** 排序号 */
    private Integer sortNo;

    /** WIP 上限（0 表示不限） */
    private Integer wipLimit;
}
