package com.bluewhale.doc.application.service;

import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.interfaces.dto.DocApprovalCallbackRequest;
import com.bluewhale.doc.interfaces.vo.DocApprovalVO;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.support.DocOperator;

/**
 * 发布审批应用服务（M3）。
 *
 * @author BlueWhale
 */
public interface DocApprovalService {

    /**
     * 提交发布审批；审批关闭时直接发布。
     *
     * @param pageId   页面ID
     * @param operator 当前操作人
     * @return 保存结果
     */
    DocPageSaveVO submit(Long pageId, DocOperator operator);

    /**
     * 查询页面审批状态。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 审批状态
     */
    DocApprovalVO status(Long pageId, Long userId);

    /**
     * 处理审批结果回调（幂等）。
     *
     * @param request 回调请求
     * @return 是否触发状态变更
     */
    boolean handleCallback(DocApprovalCallbackRequest request);

    /**
     * 待审批页面读取时同步流程状态（失败仅告警）。
     *
     * @param page 页面
     */
    void syncIfPending(DocPage page);
}
