package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作项关联视图对象
 *
 * @author BlueWhale
 */
@Data
public class WorkItemLinkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 源工作项ID */
    private String sourceId;

    /** 目标工作项ID */
    private String targetId;

    /** 目标工作项键 */
    private String targetKey;

    /** 目标工作项标题 */
    private String targetTitle;

    /** 关联类型 */
    private String linkType;

    /** 创建时间 */
    private LocalDateTime createTime;
}
