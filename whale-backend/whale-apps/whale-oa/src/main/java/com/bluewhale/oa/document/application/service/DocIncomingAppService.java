package com.bluewhale.oa.document.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;

/**
 * 收文应用服务。
 *
 * @author BlueWhale
 */
public interface DocIncomingAppService {

    /**
     * 分页查询收文。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocumentViews.Incoming> page(DocQuery query);

    /**
     * 收文详情。
     *
     * @param id 收文ID
     * @return 详情
     */
    DocumentViews.Incoming detail(Long id);

    /**
     * 收文登记。
     *
     * @param command 收文命令
     * @return 收文
     */
    DocumentViews.Incoming create(DocumentCommands.IncomingSave command);

    /**
     * 编辑登记。
     *
     * @param id      收文ID
     * @param command 收文命令
     * @return 收文
     */
    DocumentViews.Incoming update(Long id, DocumentCommands.IncomingSave command);

    /**
     * 删除登记。
     *
     * @param id 收文ID
     */
    void delete(Long id);

    /**
     * 批阅。
     *
     * @param id      收文ID
     * @param command 批阅命令
     * @return 收文
     */
    DocumentViews.Incoming review(Long id, DocumentCommands.IncomingReview command);

    /**
     * 转发。
     *
     * @param id      收文ID
     * @param command 转发命令
     * @return 收文
     */
    DocumentViews.Incoming forward(Long id, DocumentCommands.IncomingForward command);

    /**
     * 办结。
     *
     * @param id 收文ID
     * @return 收文
     */
    DocumentViews.Incoming complete(Long id);

    /**
     * 归档。
     *
     * @param id 收文ID
     * @return 收文
     */
    DocumentViews.Incoming archive(Long id);
}
