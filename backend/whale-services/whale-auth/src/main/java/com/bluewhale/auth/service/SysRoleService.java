package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
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

    /** 分页查询（keyword 模糊匹配角色编码/名称） */
    PageResult<RoleVO> page(long current, long size, String keyword);

    RoleVO getDetail(Long id);

    RoleVO create(RoleCreateRequest request);

    RoleVO update(Long id, RoleUpdateRequest request);

    /** 逻辑删除角色，并清理角色-权限关联 */
    void delete(Long id);

    /** 全量覆盖分配权限（权限点 + 菜单） */
    void assignPermissions(Long id, List<Long> permissionIds);

    /** 查询角色已分配的权限 ID 列表 */
    List<Long> listPermissionIds(Long id);

    /** 查询用户启用角色的 code 列表 */
    List<String> listRoleCodesByUserId(Long userId);

    /** 查询用户全部启用角色关联的权限 code 列表（用于 @RequirePermission 兜底校验） */
    List<String> listPermissionCodesByUserId(Long userId);
}
