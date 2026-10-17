package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.bluewhale.auth.dto.RoleCreateRequest;
import com.bluewhale.auth.dto.RoleUpdateRequest;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.vo.RoleVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.List;

/**
 * 系统角色服务
 *
 * @author BlueWhale
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 分页查询（keyword 模糊匹配角色编码/名称）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 角色编码或名称模糊匹配关键字（可空）
     * @return 角色分页结果
     */
    PageResult<RoleVO> page(long current, long size, String keyword);

    /**
     * 角色详情
     *
     * @param id 角色 ID
     * @return 角色视图对象
     */
    RoleVO getDetail(Long id);

    /**
     * 创建角色
     *
     * @param request 角色创建请求参数
     * @return 新建角色视图对象
     */
    RoleVO create(RoleCreateRequest request);

    /**
     * 更新角色
     *
     * @param id 角色 ID
     * @param request 角色更新请求参数
     * @return 更新后的角色视图对象
     */
    RoleVO update(Long id, RoleUpdateRequest request);

    /**
     * 逻辑删除角色，并清理角色-权限关联
     *
     * @param id 角色 ID
     */
    void delete(Long id);

    /**
     * 全量覆盖分配权限（权限点 + 菜单）
     *
     * @param id 角色 ID
     * @param permissionIds 目标权限点 ID 列表
     */
    void assignPermissions(Long id, List<Long> permissionIds);

    /**
     * 查询角色已分配的权限 ID 列表
     *
     * @param id 角色 ID
     * @return 角色已分配的权限点 ID 列表
     */
    List<Long> listPermissionIds(Long id);

    /**
     * 查询用户启用角色的 code 列表
     *
     * @param userId 用户 ID
     * @return 用户启用角色的 code 列表
     */
    List<String> listRoleCodesByUserId(Long userId);

    /**
     * 查询用户全部启用角色关联的权限 code 列表（用于 @RequirePermission 兜底校验）
     *
     * @param userId 用户 ID
     * @return 用户全部启用角色关联的权限 code 列表
     */
    List<String> listPermissionCodesByUserId(Long userId);
}
