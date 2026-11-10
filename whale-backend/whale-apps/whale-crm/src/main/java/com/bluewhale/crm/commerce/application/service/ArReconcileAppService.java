package com.bluewhale.crm.commerce.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.commerce.application.view.ArSnapshotView;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.commerce.domain.model.ArSnapshotQuery;

/**
 * 客户应收对账应用服务。
 *
 * @author BlueWhale
 */
public interface ArReconcileAppService {

    /**
     * 分页查询应收快照。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 分页
     */
    PageResult<ArSnapshotView> page(ArSnapshotQuery query, CrmActor actor);

    /**
     * 手动同步当日应收快照。
     *
     * @param actor 当前操作人
     * @return 同步结果
     */
    ArSyncResultView sync(CrmActor actor);

    /**
     * 定时同步当日应收快照（全量客户）。
     *
     * @return 同步结果
     */
    ArSyncResultView syncAll();
}
