package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 看板列视图（含列内工作项与 WIP 状态）
 *
 * @author BlueWhale
 */
@Data
public class BoardColumnViewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 列ID */
    private String id;

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

    /** 当前工作项数量 */
    private Integer itemCount;

    /** 是否超出 WIP 上限 */
    private Boolean overloaded;

    /** 列内工作项 */
    private List<WorkItemVO> items;
}
