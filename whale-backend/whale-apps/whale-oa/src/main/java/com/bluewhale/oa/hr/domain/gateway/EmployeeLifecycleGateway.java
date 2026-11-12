package com.bluewhale.oa.hr.domain.gateway;

import com.bluewhale.oa.hr.domain.model.EmployeeChange;

/**
 * Auth 人员生命周期网关端口。
 *
 * <p>供异动执行时同步 Auth 账号状态与部门；实现位于基础设施层，
 * 通过内网接口 {@code POST /user/v1/internal/lifecycle} 调用 Auth。</p>
 *
 * @author BlueWhale
 */
public interface EmployeeLifecycleGateway {

    /**
     * 执行生命周期同步。
     *
     * @param change 异动单
     * @return 生命周期结果
     */
    LifecycleResult apply(EmployeeChange change);
}
