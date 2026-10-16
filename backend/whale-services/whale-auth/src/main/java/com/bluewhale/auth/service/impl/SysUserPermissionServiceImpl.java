package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bluewhale.auth.dto.UpdateAttrsRequest;
import com.bluewhale.auth.entity.SysUserPermission;
import com.bluewhale.auth.mapper.SysUserPermissionMapper;
import com.bluewhale.auth.service.SysUserPermissionService;
import com.bluewhale.auth.vo.UserAttrVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户 ABAC 属性服务实现
 *
 * @author BlueWhale
 */
@Service
public class SysUserPermissionServiceImpl extends ServiceImpl<SysUserPermissionMapper, SysUserPermission>
        implements SysUserPermissionService {

    @Override
    public List<UserAttrVO> listByUserId(Long userId) {
        return list(new LambdaQueryWrapper<SysUserPermission>()
                        .eq(SysUserPermission::getUserId, userId))
                .stream().map(UserAttrVO::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceUserAttrs(Long userId, UpdateAttrsRequest request) {
        // 关联表实体无 deleted 字段，走物理删除，避免唯一键 uk_user_perm_attr 冲突
        remove(new LambdaQueryWrapper<SysUserPermission>()
                .eq(SysUserPermission::getUserId, userId));
        if (request.getAttrs().isEmpty()) {
            return;
        }
        List<SysUserPermission> rows = request.getAttrs().stream().map(item -> {
            SysUserPermission row = new SysUserPermission();
            row.setUserId(userId);
            row.setPermissionId(item.getPermissionId());
            row.setAttrKey(item.getAttrKey());
            row.setAttrValue(item.getAttrValue());
            return row;
        }).toList();
        saveBatch(rows);
    }
}
