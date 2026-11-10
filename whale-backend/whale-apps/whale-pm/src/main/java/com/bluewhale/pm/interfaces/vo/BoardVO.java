package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板视图对象（含列配置，ID 一律字符串）
 *
 * @author BlueWhale
 */
@Data
public class BoardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目ID */
    private String projectId;

    /** 看板名称 */
    private String name;

    /** 看板类型 */
    private String boardType;

    /** 列配置 */
    private List<BoardColumnVO> columns;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
