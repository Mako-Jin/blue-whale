package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批人解析规则聚合。
 *
 * <p>一个业务类型的同一节点只保留一条规则；发起时按规则解析成账号列表并写入
 * {@link ApprovalRoute} 快照，中途组织变更不影响在途实例。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRouteRule {

    /** 主键 */
    private Long id;

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
    private int sortNo;

    /** 是否启用 */
    private boolean enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
