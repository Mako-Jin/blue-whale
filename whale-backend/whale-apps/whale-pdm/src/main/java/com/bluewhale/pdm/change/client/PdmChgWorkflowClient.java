package com.bluewhale.pdm.change.client;

/**
 * 变更审批工作流客户端
 *
 * <p>审批引擎归 OA / whale-workflow；PDM 只发起实例并保存实例号与状态快照。</p>
 *
 * @author BlueWhale
 */
public interface PdmChgWorkflowClient {

    /**
     * 发起审批实例。
     *
     * @param tenantId 租户ID
     * @param bizType  业务类型：ECR/ECO
     * @param bizId    业务ID
     * @param bizNo    业务编号
     * @param title    实例标题
     * @param userId   发起人
     * @return 审批实例ID
     */
    Long start(Long tenantId, String bizType, Long bizId, String bizNo, String title, Long userId);
}
