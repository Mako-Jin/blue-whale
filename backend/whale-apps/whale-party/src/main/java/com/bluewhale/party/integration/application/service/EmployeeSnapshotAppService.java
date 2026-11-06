package com.bluewhale.party.integration.application.service;

import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.integration.application.view.EmployeeSyncResultView;

/**
 * 员工只读快照应用服务（M-P4）。
 *
 * @author BlueWhale
 */
public interface EmployeeSnapshotAppService {

    /**
     * 从 ERP 同步员工目录（按页拉取 + 逐条 upsert，不先清表）。
     *
     * @param actor 操作人
     * @return 同步结果
     */
    EmployeeSyncResultView sync(PartyActor actor);

    /**
     * 在职员工数（覆盖率分母）。
     *
     * @return 在职员工数
     */
    long activeEmployeeCount();
}
