package com.bluewhale.pm.interfaces.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作项排序请求：移动到 beforeItemId 之后、afterItemId 之前。
 *
 * @author BlueWhale
 */
@Data
public class WorkItemRankRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 前一个工作项ID（可为空，表示移动到首位） */
    private Long beforeItemId;

    /** 后一个工作项ID（可为空，表示移动到末位） */
    private Long afterItemId;
}
