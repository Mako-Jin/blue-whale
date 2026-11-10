package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.dto.DocTemplateApplyRequest;
import com.bluewhale.doc.interfaces.dto.DocTemplateSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.interfaces.vo.DocTemplateVO;

import java.util.List;

/**
 * 文档模板应用服务（M3）。
 *
 * @author BlueWhale
 */
public interface DocTemplateService {

    /**
     * 查询可用模板：租户级模板与指定空间模板。
     *
     * @param spaceId 空间ID，可为 {@code null}
     * @param userId  当前用户ID
     * @return 模板列表
     */
    List<DocTemplateVO> list(Long spaceId, Long userId);

    /**
     * 查询模板详情。
     *
     * @param templateId 模板ID
     * @param userId     当前用户ID
     * @return 模板详情
     */
    DocTemplateVO detail(Long templateId, Long userId);

    /**
     * 新建模板。
     *
     * @param request 保存请求
     * @param userId  当前用户ID
     * @return 模板详情
     */
    DocTemplateVO create(DocTemplateSaveRequest request, Long userId);

    /**
     * 更新模板。
     *
     * @param templateId 模板ID
     * @param request    保存请求
     * @param userId     当前用户ID
     * @return 模板详情
     */
    DocTemplateVO update(Long templateId, DocTemplateSaveRequest request, Long userId);

    /**
     * 删除模板（逻辑删除）。
     *
     * @param templateId 模板ID
     * @param userId     当前用户ID
     */
    void delete(Long templateId, Long userId);

    /**
     * 从模板创建页面。
     *
     * @param templateId 模板ID
     * @param request    创建请求
     * @param userId     当前用户ID
     * @return 新建页面详情
     */
    DocPageVO apply(Long templateId, DocTemplateApplyRequest request, Long userId);
}
