package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bluewhale.auth.dto.PermissionRequest;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.mapper.SysPermissionMapper;
import com.bluewhale.auth.service.SysPermissionService;
import com.bluewhale.auth.vo.PermissionVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统权限点服务实现
 *
 * @author BlueWhale
 */
@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {

    @Override
    public PageResult<PermissionVO> page(long current, long size, String keyword, String permType) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysPermission::getPermCode, keyword)
                    .or().like(SysPermission::getPermName, keyword));
        }
        if (StringUtils.hasText(permType)) {
            wrapper.eq(SysPermission::getPermType, permType);
        }
        wrapper.orderByAsc(SysPermission::getSortNo).orderByDesc(SysPermission::getCreateTime);
        Page<SysPermission> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        List<PermissionVO> records = page.getRecords().stream().map(PermissionVO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PermissionVO getDetail(Long id) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return PermissionVO.from(permission);
    }

    @Override
    public void create(PermissionRequest request) {
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, request.getPermCode()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        copyProperties(request, permission);
        save(permission);
    }

    @Override
    public void update(Long id, PermissionRequest request) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, request.getPermCode())
                .ne(SysPermission::getId, id));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "权限编码已存在");
        }
        copyProperties(request, permission);
        updateById(permission);
    }

    @Override
    public void delete(Long id) {
        Long children = baseMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子权限，不允许删除");
        }
        removeById(id);
    }

    private void copyProperties(PermissionRequest request, SysPermission permission) {
        permission.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        permission.setPermCode(request.getPermCode());
        permission.setPermName(request.getPermName());
        permission.setPermType(request.getPermType());
        permission.setPath(request.getPath());
        permission.setMethod(request.getMethod());
        permission.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        permission.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}
