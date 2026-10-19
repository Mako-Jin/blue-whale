package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.entity.UserRoleAssignment;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.entity.SysUserRole;
import com.bluewhale.auth.infrastructure.persistence.assembler.UserPersistenceAssembler;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link UserRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-8）：以既有 Mapper 完成
 * PO 读写，并借 {@link UserPersistenceAssembler} 完成 PO ↔ 领域对象转换。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public void save(User user) {
        SysUser po = UserPersistenceAssembler.toPo(user);
        if (po.getId() == null) {
            userMapper.insert(po);
        } else {
            userMapper.updateById(po);
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        SysUser po = userMapper.selectById(id);
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        SysUser po = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        return Optional.ofNullable(po).map(UserPersistenceAssembler::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        return count != null && count > 0;
    }

    @Override
    public void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            userRoleMapper.insert(UserPersistenceAssembler.toAssignmentPo(UserRoleAssignment.of(userId, roleId)));
        }
    }
}
