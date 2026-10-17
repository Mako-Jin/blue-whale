package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.dto.UserCreateRequest;
import com.bluewhale.auth.dto.UserUpdateRequest;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.entity.SysRole;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.mapper.SysRoleMapper;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import com.bluewhale.auth.service.SysUserService;
import com.bluewhale.auth.vo.UserVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统用户服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> page(long current, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        List<UserVO> records = page.getRecords().stream().map(UserVO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public UserVO getDetail(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return UserVO.from(user);
    }

    @Override
    public UserVO create(UserCreateRequest request) {
        // 值对象前置校验用户名不变量（AUTH-14 / T-DDD-2）
        String username = Username.of(request.getUsername()).value();
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(EncryptedPassword.of(passwordEncoder.encode(request.getPassword())).value());
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setDeptId(request.getDeptId());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        save(user);
        return UserVO.from(user);
    }

    @Override
    public UserVO update(Long id, UserUpdateRequest request) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setDeptId(request.getDeptId());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        updateById(user);
        return UserVO.from(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        removeById(id);
        // 清理用户-角色关联（关联表实体无 deleted 字段，走物理删除）
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!roleIds.isEmpty()) {
            Long roleCount = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));
            if (roleCount == null || roleCount != roleIds.size()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在无效的角色ID");
            }
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        for (Long roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(id);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    @Override
    public List<Long> listRoleIds(Long id) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id))
                .stream().map(SysUserRole::getRoleId).toList();
    }
}
