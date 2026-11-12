package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.aggregate.UserPermissionAttributeAggregate;
import com.bluewhale.auth.domain.repository.UserPermissionRepository;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserPermissionPo;
import com.bluewhale.auth.infrastructure.persistence.assembler.UserPermissionPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link UserPermissionRepository} 的 MyBatis-Plus 实现（REQ-ARCH-001 / T-DDD-9）：以既有
 * {@link SysUserPermissionMapper} 完成 PO 读写，并借 {@link UserPermissionPersistenceAssembler}
 * 完成 PO ↔ 领域对象转换。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UserPermissionRepositoryImpl implements UserPermissionRepository {

    private final SysUserPermissionMapper userPermissionMapper;

    @Override
    public List<UserPermissionAttributeAggregate> listByUserId(Long userId) {
        return userPermissionMapper.selectList(new LambdaQueryWrapper<SysUserPermissionPo>()
                        .eq(SysUserPermissionPo::getUserId, userId))
                .stream().map(UserPermissionPersistenceAssembler::toDomain).toList();
    }

    @Override
    public void replaceAll(Long userId, List<UserPermissionAttributeAggregate> attributes) {
        // 关联表实体无 deleted 字段，走物理删除，避免唯一键 uk_user_perm_attr 冲突
        userPermissionMapper.delete(new LambdaQueryWrapper<SysUserPermissionPo>()
                .eq(SysUserPermissionPo::getUserId, userId));
        for (UserPermissionAttributeAggregate attribute : attributes) {
            userPermissionMapper.insert(UserPermissionPersistenceAssembler.toPo(attribute));
        }
    }
}
