package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.bluewhale.auth.dto.ChangePasswordRequest;
import com.bluewhale.auth.dto.UserCreateRequest;
import com.bluewhale.auth.dto.UserStatusRequest;
import com.bluewhale.auth.dto.UserUpdateRequest;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.vo.UserVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.List;

/**
 * 系统用户服务
 *
 * @author BlueWhale
 */
public interface SysUserService extends IService<SysUser> {

    /** 分页查询（keyword 模糊匹配用户名/昵称/手机号） */
    PageResult<UserVO> page(long current, long size, String keyword);

    UserVO getDetail(Long id);

    UserVO create(UserCreateRequest request);

    UserVO update(Long id, UserUpdateRequest request);

    /** 重置用户登录密码（管理端操作，明文经 BCrypt 加密后落库） */
    void changePassword(Long id, ChangePasswordRequest request);

    /** 变更用户启停状态（1 启用 / 0 停用） */
    void changeStatus(Long id, UserStatusRequest request);

    /** 逻辑删除用户，并清理用户-角色关联 */
    void delete(Long id);

    /** 全量覆盖分配角色 */
    void assignRoles(Long id, List<Long> roleIds);

    /** 查询用户已分配的角色 ID 列表 */
    List<Long> listRoleIds(Long id);
}
