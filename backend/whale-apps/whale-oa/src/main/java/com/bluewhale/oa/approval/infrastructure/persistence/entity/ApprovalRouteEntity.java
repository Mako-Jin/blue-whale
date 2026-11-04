package com.bluewhale.oa.approval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批人快照实体，对应表 {@code oa_approval_route}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_route")
public class ApprovalRouteEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** Workflow 节点标识 */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 审批人用户ID */
    private Long assigneeId;

    /** 审批人账号 */
    private String assigneeAccount;

    /** 解析来源类型 */
    private String resolverType;

    /** 排序号 */
    private Integer sortNo;
}
