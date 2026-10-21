package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程定义版本快照视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ProcessDefinitionVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照主键ID */
    private Long id;

    /** 流程定义ID */
    private Long definitionId;

    /** 版本号 */
    private Integer version;

    /** 该版本的流程定义 JSON 快照 */
    private String definitionJson;

    /** 发布状态：0 草稿 / 1 已发布 */
    private Integer publishStatus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
