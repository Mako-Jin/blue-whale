package com.bluewhale.message.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.MessageTemplateQueryRequest;
import com.bluewhale.message.dto.MessageTemplateSaveRequest;
import com.bluewhale.message.vo.MessageTemplateVO;

/**
 * 消息模板管理服务。
 *
 * @author BlueWhale
 */
public interface MessageTemplateService {

    /**
     * 分页查询模板。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MessageTemplateVO> page(MessageTemplateQueryRequest request);

    /**
     * 查询模板详情。
     *
     * @param id 模板ID
     * @return 模板详情
     */
    MessageTemplateVO detail(Long id);

    /**
     * 新增模板。
     *
     * @param request 新增请求
     * @return 新增后的模板
     */
    MessageTemplateVO create(MessageTemplateSaveRequest request);

    /**
     * 更新模板。
     *
     * @param id      模板ID
     * @param request 更新请求
     * @return 更新后的模板
     */
    MessageTemplateVO update(Long id, MessageTemplateSaveRequest request);

    /**
     * 删除模板（逻辑删除）。
     *
     * @param id 模板ID
     */
    void delete(Long id);
}
