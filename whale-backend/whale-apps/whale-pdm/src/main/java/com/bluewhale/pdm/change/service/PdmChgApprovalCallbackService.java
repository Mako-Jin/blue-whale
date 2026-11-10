package com.bluewhale.pdm.change.service;

import com.bluewhale.pdm.change.dto.PdmChgApprovalCallbackRequest;

/**
 * 变更审批回调服务
 *
 * <p>回调按审批实例号定位 ECR/ECO，重复回调不重复流转。</p>
 *
 * @author BlueWhale
 */
public interface PdmChgApprovalCallbackService {

    /**
     * 处理审批回调。
     *
     * @param request 回调请求
     */
    void handle(PdmChgApprovalCallbackRequest request);
}
