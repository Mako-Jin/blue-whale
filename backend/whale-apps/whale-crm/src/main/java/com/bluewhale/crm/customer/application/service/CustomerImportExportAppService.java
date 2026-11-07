package com.bluewhale.crm.customer.application.service;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.customer.application.view.CustomerImportPreviewView;
import com.bluewhale.crm.customer.application.view.CustomerImportResultView;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;

/**
 * CSV 客户导入导出应用服务（M1-9）。
 *
 * @author BlueWhale
 */
public interface CustomerImportExportAppService {

    /**
     * 导入预检：逐行校验与查重，不落库。
     *
     * @param content CSV 内容
     * @param actor 当前操作人
     * @return 预检结果
     */
    CustomerImportPreviewView preview(String content, CrmActor actor);

    /**
     * 执行导入：事务内仅创建通过校验且非重复的行。
     *
     * @param content CSV 内容
     * @param actor 当前操作人
     * @return 导入结果
     */
    CustomerImportResultView execute(String content, CrmActor actor);

    /**
     * 导出客户 CSV。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return CSV 文本
     */
    String export(CustomerQuery query, CrmActor actor);
}
