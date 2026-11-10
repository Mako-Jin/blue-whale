package com.bluewhale.crm.service.domain.port;

import com.bluewhale.crm.common.CrmActor;

import java.util.Map;

/**
 * 退换货审批流程端口（由基础设施适配 whale-workflow）。
 *
 * @author BlueWhale
 */
public interface ReturnApprovalPort {

    /**
     * 发起审批流程实例。
     *
     * @param definitionId 流程定义 ID
     * @param title 实例标题
     * @param businessKey 业务键
     * @param variables 流程变量
     * @param actor 当前操作人（透传身份与权限码头）
     * @return 流程实例 ID
     */
    Long start(Long definitionId, String title, String businessKey, Map<String, Object> variables, CrmActor actor);
}
