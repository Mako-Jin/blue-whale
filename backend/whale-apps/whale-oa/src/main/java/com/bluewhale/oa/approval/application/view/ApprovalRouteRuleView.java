package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批人解析规则视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRouteRuleView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 业务类型 */
    private String businessType;

    /** 节点标识 */
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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
