package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.bluewhale.auth.dto.PermissionRequest;
import com.bluewhale.auth.entity.SysPermission;
import com.bluewhale.auth.vo.PermissionVO;
import com.bluewhale.common.core.result.PageResult;

/**
 * 系统权限点服务
 *
 * @author BlueWhale
 */
public interface SysPermissionService extends IService<SysPermission> {

    /** 分页查询（keyword 模糊匹配权限编码/名称，permType 精确过滤） */
    PageResult<PermissionVO> page(long current, long size, String keyword, String permType);

    PermissionVO getDetail(Long id);

    void create(PermissionRequest request);

    void update(Long id, PermissionRequest request);

    void delete(Long id);
}
