package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveRoleConstraintCommand;
import com.bluewhale.auth.application.model.query.ConstraintPageQuery;
import com.bluewhale.auth.application.model.vo.RoleConstraintVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 角色约束应用服务（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
public interface RoleConstraintAppService {

    /**
     * 新建约束
     *
     * @param command 保存命令
     * @return 视图对象
     */
    RoleConstraintVO create(SaveRoleConstraintCommand command);

    /**
     * 更新约束
     *
     * @param id 主键
     * @param command 保存命令
     * @return 视图对象
     */
    RoleConstraintVO update(Long id, SaveRoleConstraintCommand command);

    /**
     * 删除约束
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 查询约束详情
     *
     * @param id 主键
     * @return 视图对象
     */
    RoleConstraintVO get(Long id);

    /**
     * 分页查询约束
     *
     * @param query 分页查询
     * @return 分页结果
     */
    PageResult<RoleConstraintVO> page(ConstraintPageQuery query);
}
