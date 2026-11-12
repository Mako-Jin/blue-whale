package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.AssignRolePermissionsCommand;
import com.bluewhale.auth.application.model.command.CreateRoleCommand;
import com.bluewhale.auth.application.model.command.UpdateRoleCommand;
import com.bluewhale.auth.application.model.query.RolePageQuery;
import com.bluewhale.auth.application.model.vo.RoleVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.List;

/**
 * 角色管理应用服务：编排角色聚合与角色-权限关联的管理端用例（REQ-ARCH-001 / T-DDD-9）。
 *
 * <p>依赖方向遵循 DDD 四层约定：仅依赖领域层聚合/值对象/仓储端口与领域事件发布端口，不依赖
 * {@code entity}/{@code mapper}/{@code infrastructure}/{@code service}。返回视图对象或
 * {@link PageResult}，不向调用方泄漏领域对象与持久化对象。</p>
 *
 * <p>语义与旧 {@code SysRoleServiceImpl} 逐条对齐：同样的校验顺序、异常文案与业务码；
 * 角色删除与权限变更后发布领域事件，由基础设施层监听器在事务提交后失效该角色下用户的权限缓存；
 * {@code assignPermissions} 保留与旧实现一致的分布式锁（锁 {@code lock:auth:role:{id}}），
 * 事务边界仅放在多写方法上。</p>
 *
 * @author BlueWhale
 */
public interface RoleAppService {

    /**
     * 分页查询角色（关键字模糊匹配角色编码/名称，按排序号升序、创建时间降序）
     *
     * @param query 角色分页查询条件
     * @return 角色分页结果
     */
    PageResult<RoleVO> page(RolePageQuery query);

    /**
     * 查询角色详情
     *
     * @param id 角色 ID
     * @return 角色视图对象
     */
    RoleVO getDetail(Long id);

    /**
     * 创建角色：角色编码值对象前置校验 → 编码唯一性校验 → 保存
     *
     * @param command 创建角色命令
     * @return 新建角色视图对象
     */
    RoleVO create(CreateRoleCommand command);

    /**
     * 更新角色：角色编码不可修改，名称/描述全量覆盖，排序号与状态非空时变更
     *
     * @param id 角色 ID
     * @param command 更新角色命令
     * @return 更新后的角色视图对象
     */
    RoleVO update(Long id, UpdateRoleCommand command);

    /**
     * 删除角色：逻辑删除角色并清理角色-权限关联，随后发布删除事件
     *
     * @param id 角色 ID
     */
    void delete(Long id);

    /**
     * 全量覆盖角色权限：校验权限点有效性后重建关联，并发布权限分配事件
     *
     * @param id 角色 ID
     * @param command 分配权限命令
     */
    void assignPermissions(Long id, AssignRolePermissionsCommand command);

    /**
     * 查询角色已分配的权限点 ID 列表
     *
     * @param id 角色 ID
     * @return 权限点 ID 列表
     */
    List<Long> listPermissionIds(Long id);

    /**
     * 查询用户拥有的启用角色编码（用户 → 角色，过滤停用角色）
     *
     * <p>与旧 {@code SysRoleServiceImpl#listRoleCodesByUserId} 逐行等价：先查用户角色 ID，
     * 为空直接返回空列表，再按启用状态过滤并映射为角色编码。</p>
     *
     * @param userId 用户 ID
     * @return 启用角色编码列表；用户无角色时返回空列表
     */
    List<String> listRoleCodesByUserId(Long userId);

    /**
     * 查询用户经由角色获得的启用权限码（用户 → 角色 → 权限点，逐层去重并过滤停用）
     *
     * <p>与旧 {@code SysRoleServiceImpl#listPermissionCodesByUserId} 逐行等价：先查用户角色 ID，
     * 为空返回空列表；再查角色关联权限点 ID（去重），为空返回空列表；最后仅保留启用权限点的
     * 权限码（去重）。</p>
     *
     * @param userId 用户 ID
     * @return 权限码列表；用户无角色或无有效权限关联时返回空列表
     */
    List<String> listPermissionCodesByUserId(Long userId);
}
