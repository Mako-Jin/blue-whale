package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SavePermissionStrategyCommand;
import com.bluewhale.auth.application.model.query.StrategyPageQuery;
import com.bluewhale.auth.application.model.vo.PermissionStrategyVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 权限策略应用服务（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
public interface PermissionStrategyAppService {

    /**
     * 新建策略
     *
     * @param command 保存命令
     * @return 视图对象
     */
    PermissionStrategyVO create(SavePermissionStrategyCommand command);

    /**
     * 更新策略
     *
     * @param id 主键
     * @param command 保存命令
     * @return 视图对象
     */
    PermissionStrategyVO update(Long id, SavePermissionStrategyCommand command);

    /**
     * 删除策略
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 查询策略详情
     *
     * @param id 主键
     * @return 视图对象
     */
    PermissionStrategyVO get(Long id);

    /**
     * 分页查询策略
     *
     * @param query 分页查询
     * @return 分页结果
     */
    PageResult<PermissionStrategyVO> page(StrategyPageQuery query);

    /**
     * 设置为默认策略（同租户仅一条，其余自动取消）
     *
     * @param id 主键
     * @return 视图对象
     */
    PermissionStrategyVO setDefault(Long id);
}
