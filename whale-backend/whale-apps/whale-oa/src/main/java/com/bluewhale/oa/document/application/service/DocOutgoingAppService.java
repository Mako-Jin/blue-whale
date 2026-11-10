package com.bluewhale.oa.document.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;

/**
 * 发文应用服务。
 *
 * @author BlueWhale
 */
public interface DocOutgoingAppService {

    /**
     * 分页查询发文。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocumentViews.Outgoing> page(DocQuery query);

    /**
     * 发文详情。
     *
     * @param id 发文ID
     * @return 详情
     */
    DocumentViews.Outgoing detail(Long id);

    /**
     * 拟稿。
     *
     * @param command    发文命令
     * @param drafterId  拟稿人ID
     * @param drafterName 拟稿人姓名
     * @return 发文
     */
    DocumentViews.Outgoing create(DocumentCommands.OutgoingSave command, Long drafterId, String drafterName);

    /**
     * 编辑草稿/驳回单据。
     *
     * @param id      发文ID
     * @param command 发文命令
     * @return 发文
     */
    DocumentViews.Outgoing update(Long id, DocumentCommands.OutgoingSave command);

    /**
     * 删除草稿/驳回单据。
     *
     * @param id 发文ID
     */
    void delete(Long id);

    /**
     * 提交审核。
     *
     * @param id 发文ID
     * @return 发文
     */
    DocumentViews.Outgoing submit(Long id);

    /**
     * 审核。
     *
     * @param id      发文ID
     * @param command 审核命令
     * @return 发文
     */
    DocumentViews.Outgoing audit(Long id, DocumentCommands.OutgoingAudit command);

    /**
     * 签发。
     *
     * @param id      发文ID
     * @param command 签发命令
     * @return 发文
     */
    DocumentViews.Outgoing sign(Long id, DocumentCommands.OutgoingSign command);

    /**
     * 下发。
     *
     * @param id 发文ID
     * @return 发文
     */
    DocumentViews.Outgoing issue(Long id);

    /**
     * 归档。
     *
     * @param id 发文ID
     * @return 发文
     */
    DocumentViews.Outgoing archive(Long id);

    /**
     * 同步审批结果。
     *
     * @param businessId 发文ID
     * @param result     审批结果
     */
    void syncApprovalResult(Long businessId, String result);
}
