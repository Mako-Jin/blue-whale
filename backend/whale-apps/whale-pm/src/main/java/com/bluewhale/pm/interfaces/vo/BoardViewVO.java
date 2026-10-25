package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 看板执行视图（列 + 列内工作项）
 *
 * @author BlueWhale
 */
@Data
public class BoardViewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 看板ID */
    private String id;

    /** 项目ID */
    private String projectId;

    /** 看板名称 */
    private String name;

    /** 看板类型 */
    private String boardType;

    /** 列视图 */
    private List<BoardColumnViewVO> columns;
}
