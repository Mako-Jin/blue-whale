package com.bluewhale.workflow.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程实例实体，对应表 {@code process_instance}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code process_instance} 列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("process_instance")
public class ProcessInstancePo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流程定义 ID */
    private Long definitionId;

    /** 发起时的流程定义编码（冗余，便于查询与展示） */
    private String definitionCode;

    /** 发起时的流程定义版本（冗余快照） */
    private Integer definitionVersion;

    /** 实例标题 */
    private String title;

    /** 业务键（可选，用于关联外部业务单据） */
    private String businessKey;

    /** 状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 办结时间（审批通过时写入；历史数据为空时归档侧回落 update_time） */
    private LocalDateTime finishedAt;

    /** 当前审批节点下标（0 起） */
    private Integer currentIndex;

    /** 当前审批节点键（规范化定义；为空时回落 {@code currentIndex}） */
    private String currentNodeKey;

    /** 发起人账号 */
    private String initiator;

    /** 流程变量 JSON（条件选路依据，最长 64KB，可空） */
    private String variablesJson;

    /** 当前并行活动节点键集合（逗号分隔；单待办时为空，空即代表无并行分支） */
    private String activeNodeKeys;

    /** 发起时审批人覆盖快照（nodeKey -> 账号列表 JSON；为空表示全部使用定义中的静态审批人） */
    private String assigneeOverridesJson;
}
