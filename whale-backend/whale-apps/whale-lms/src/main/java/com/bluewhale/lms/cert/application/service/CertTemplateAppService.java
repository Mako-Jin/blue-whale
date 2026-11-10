package com.bluewhale.lms.cert.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.cert.application.command.CertTemplateSaveCommand;
import com.bluewhale.lms.cert.application.view.CertTemplateView;
import com.bluewhale.lms.cert.domain.model.CertTemplateQuery;
import com.bluewhale.lms.common.LmsActor;

/**
 * 证书模板应用服务。
 *
 * @author BlueWhale
 */
public interface CertTemplateAppService {

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<CertTemplateView> page(CertTemplateQuery query, LmsActor actor);

    /**
     * 模板详情。
     *
     * @param id 模板 ID
     * @param actor 操作人
     * @return 详情
     */
    CertTemplateView detail(Long id, LmsActor actor);

    /**
     * 新建模板。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 模板 ID
     */
    Long create(CertTemplateSaveCommand command, LmsActor actor);

    /**
     * 编辑模板。
     *
     * @param id 模板 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long id, CertTemplateSaveCommand command, LmsActor actor);

    /**
     * 删除模板（已被证书引用时拒绝）。
     *
     * @param id 模板 ID
     * @param actor 操作人
     */
    void delete(Long id, LmsActor actor);

    /**
     * 启用 / 停用模板。
     *
     * @param id 模板 ID
     * @param enabled 是否启用
     * @param actor 操作人
     */
    void changeStatus(Long id, boolean enabled, LmsActor actor);
}
