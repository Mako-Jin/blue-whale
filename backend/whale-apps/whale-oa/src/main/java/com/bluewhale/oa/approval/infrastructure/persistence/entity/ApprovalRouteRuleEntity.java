package com.bluewhale.oa.approval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批人解析规则实体，对应表 {@code oa_approval_route_rule}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_route_rule")
public class ApprovalRouteRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String businessType;

    /** Workflow 节点标识 */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 解析器类型 */
    private String resolverType;

    /** 解析器参数 */
    private String resolverValue;

    /** 兜底解析器类型 */
    private String fallbackResolverType;

    /** 兜底解析器参数 */
    private String fallbackResolverValue;

    /** 排序号 */
    private Integer sortNo;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;
}
