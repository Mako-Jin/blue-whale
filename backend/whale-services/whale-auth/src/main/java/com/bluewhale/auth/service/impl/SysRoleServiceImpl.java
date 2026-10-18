package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.auth.dto.RoleCreateRequest;
import com.bluewhale.auth.dto.RoleUpdateRequest;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.entity.SysRolePermission;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysRolePermissionMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.auth.security.PermissionCache;
import com.bluewhale.auth.service.SysRoleService;
import com.bluewhale.auth.vo.RoleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统角色服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final PermissionCache permissionCache;

    @Override
    public PageResult<RoleVO> page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword)
                    .or().like(SysRole::getRoleName, keyword));
        }
        wrapper.orderByAsc(SysRole::getSortNo).orderByDesc(SysRole::getCreateTime);
        Page<SysRole> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        List<RoleVO> records = page.getRecords().stream().map(RoleVO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public RoleVO getDetail(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return RoleVO.from(role);
    }

    @Override
    public RoleVO create(RoleCreateRequest request) {
        // 值对象前置校验角色编码不变量（AUTH-14 / T-DDD-2）
        String roleCode = RoleCode.of(request.getRoleCode()).value();
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        save(role);
        return RoleVO.from(role);
    }

    @Override
    public RoleVO update(Long id, RoleUpdateRequest request) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        if (request.getSortNo() != null) {
            role.setSortNo(request.getSortNo());
        }
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        updateById(role);
        return RoleVO.from(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        removeById(id);
        // 清理角色-权限关联（关联表实体无 deleted 字段，走物理删除）
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        // 角色删除后该角色下用户的权限码集合变化，失效其缓存（提交后生效）
        permissionCache.evictByRole(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long id, List<Long> permissionIds) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!permissionIds.isEmpty()) {
            Long permCount = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                    .in(SysPermission::getId, permissionIds));
            if (permCount == null || permCount != permissionIds.size()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在无效的权限ID");
            }
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        for (Long permissionId : permissionIds) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(id);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        // 角色权限变更后该角色下用户的权限码集合变化，失效其缓存（提交后生效）
        permissionCache.evictByRole(id);
    }

    @Override
    public List<Long> listPermissionIds(Long id) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, id))
                .stream().map(SysRolePermission::getPermissionId).toList();
    }

    @Override
    public List<String> listRoleCodesByUserId(Long userId) {
        List<Long> roleIds = listRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, 1))
                .stream().map(SysRole::getRoleCode).toList();
    }

    @Override
    public List<String> listPermissionCodesByUserId(Long userId) {
        List<Long> roleIds = listRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds))
                .stream().map(SysRolePermission::getPermissionId).distinct().toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .in(SysPermission::getId, permissionIds)
                        .eq(SysPermission::getStatus, 1))
                .stream().map(SysPermission::getPermCode).distinct().toList();
    }

    /**
     * 查询用户关联的角色 ID 列表
     *
     * @param userId 用户 ID
     * @return 用户关联的角色 ID 列表
     */
    private List<Long> listRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }
}
