package com.bluewhale.oa.document.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;

import java.util.List;

/**
 * 公文模板应用服务。
 *
 * @author BlueWhale
 */
public interface DocTemplateAppService {

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DocumentViews.Template> page(DocQuery query);

    /**
     * 查询全部模板。
     *
     * @param docType 适用范围，可为空
     * @return 模板列表
     */
    List<DocumentViews.Template> all(String docType);

    /**
     * 新增模板。
     *
     * @param command 模板命令
     * @return 模板
     */
    DocumentViews.Template create(DocumentCommands.TemplateSave command);

    /**
     * 更新模板。
     *
     * @param id      模板ID
     * @param command 模板命令
     * @return 模板
     */
    DocumentViews.Template update(Long id, DocumentCommands.TemplateSave command);

    /**
     * 删除模板。
     *
     * @param id 模板ID
     */
    void delete(Long id);
}
