package com.bluewhale.crm.customer.application.service;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.customer.application.view.CustomerMergeResultView;
import com.bluewhale.crm.customer.domain.model.CustomerMergeCommand;
import com.bluewhale.crm.customer.domain.model.CustomerMergePreview;

/**
 * 客户合并应用服务（M1-4）。
 *
 * @author BlueWhale
 */
public interface CustomerMergeAppService {

    /**
     * 合并预览：校验双方状态并统计引用与冲突，不产生写入。
     *
     * @param command 合并命令
     * @param actor 当前操作人
     * @return 合并预览
     */
    CustomerMergePreview preview(CustomerMergeCommand command, CrmActor actor);

    /**
     * 执行合并：重写跨上下文引用并迁移本上下文数据，源客户置 MERGED 只读。
     *
     * @param command 合并命令
     * @param actor 当前操作人
     * @return 合并结果统计
     */
    CustomerMergeResultView merge(CustomerMergeCommand command, CrmActor actor);
}
