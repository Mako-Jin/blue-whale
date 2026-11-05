package com.bluewhale.oa.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.EmployeeProfileSaveCommand;
import com.bluewhale.oa.hr.application.view.EmployeeProfileView;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;

/**
 * 员工扩展档案应用服务。
 *
 * @author BlueWhale
 */
public interface EmployeeProfileAppService {

    /**
     * 分页查询员工档案。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EmployeeProfileView> page(EmployeeProfileQuery query);

    /**
     * 按用户ID查询档案。
     *
     * @param userId 用户ID
     * @return 档案视图
     */
    EmployeeProfileView findByUserId(Long userId);

    /**
     * 保存档案（按租户 + 用户ID 幂等 upsert）。
     *
     * @param command 保存命令
     * @return 保存后的档案
     */
    EmployeeProfileView save(EmployeeProfileSaveCommand command);

    /**
     * 按用户ID删除档案。
     *
     * @param userId 用户ID
     */
    void delete(Long userId);
}
