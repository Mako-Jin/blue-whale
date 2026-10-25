package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作项状态视图对象
 *
 * @author BlueWhale
 */
@Data
public class StatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 项目ID */
    private String projectId;

    /** 状态编码 */
    private String code;

    /** 状态名称 */
    private String name;

    /** 状态分类：TODO / IN_PROGRESS / DONE */
    private String category;

    /** 排序号 */
    private Integer sortNo;

    /** 是否默认状态 */
    private Boolean isDefault;

    /** 创建时间 */
    private LocalDateTime createTime;
}
