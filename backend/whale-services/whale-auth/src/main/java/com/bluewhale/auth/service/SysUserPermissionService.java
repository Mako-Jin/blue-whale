package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bluewhale.auth.dto.UpdateAttrsRequest;
import com.bluewhale.auth.entity.SysUserPermission;
import com.bluewhale.auth.vo.UserAttrVO;

import java.util.List;

/**
 * 用户 ABAC 属性服务（sys_user_permission）
 *
 * @author BlueWhale
 */
public interface SysUserPermissionService extends IService<SysUserPermission> {

    /** 查询用户全部属性 */
    List<UserAttrVO> listByUserId(Long userId);

    /** 全量覆盖写入用户属性 */
    void replaceUserAttrs(Long userId, UpdateAttrsRequest request);
}
