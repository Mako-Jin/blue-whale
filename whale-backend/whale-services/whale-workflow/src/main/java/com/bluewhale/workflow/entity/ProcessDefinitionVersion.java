package com.bluewhale.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义版本快照实体，对应表 {@code process_definition_version}。
 *
 * <p>每次流程定义 JSON 变更都会落一条快照，实例发起时锁定版本号，
 * 后续审批与回退按快照解析，避免「定义被改导致在途实例走错流程」。
 * 公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_definition_version")
public class ProcessDefinitionVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程定义 ID */
    private Long definitionId;

    /** 版本号（与定义表 version 对应） */
    private Integer version;

    /** 该版本的流程定义 JSON 快照 */
    private String definitionJson;

    /** 发布状态：0 草稿 / 1 已发布 */
    private Integer publishStatus;
}
