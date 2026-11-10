package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批业务配置聚合。
 *
 * <p>一种业务类型（{@code businessType}）只允许一条配置，用于解析 Workflow 定义、
 * 标题模板与默认抄送人。关键业务表单由 OA 领域模型承载，不绑定动态表单。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovalConfig {

    /** 主键 */
    private Long id;

    /** 业务类型 */
    private String businessType;

    /** 业务名称 */
    private String businessName;

    /** Workflow 流程定义编码 */
    private String definitionCode;

    /** 标题模板 */
    private String titleTemplate;

    /** 业务详情前端路由 */
    private String businessUrl;

    /** 默认抄送人ID，逗号分隔 */
    private String ccUserIds;

    /** 是否启用 */
    private boolean enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 启用配置。
     */
    public void enable() {
        enabled = true;
    }

    /**
     * 停用配置。
     */
    public void disable() {
        enabled = false;
    }
}
