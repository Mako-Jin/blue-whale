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

    /**
     * 分页查询（keyword 模糊匹配用户名/昵称/手机号）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 用户分页结果
     */
    PageResult<UserVO> page(long current, long size, String keyword);

    /**
     * 用户详情
     *
     * @param id 用户 ID
     * @return 用户视图对象
     */
    UserVO getDetail(Long id);

    /**
     * 创建用户
     *
     * @param request 用户创建请求参数
     * @return 新建用户视图对象
     */
    UserVO create(UserCreateRequest request);

    /**
     * 更新用户
     *
     * @param id 用户 ID
     * @param request 用户更新请求参数
     * @return 更新后的用户视图对象
     */
    UserVO update(Long id, UserUpdateRequest request);

    /**
     * 重置用户登录密码（管理端操作，明文经 BCrypt 加密后落库）
     *
     * @param id 用户 ID
     * @param request 密码重置请求（含新密码）
     */
    void changePassword(Long id, ChangePasswordRequest request);

    /**
     * 变更用户启停状态（1 启用 / 0 停用）
     *
     * @param id 用户 ID
     * @param request 状态变更请求
     */
    void changeStatus(Long id, UserStatusRequest request);

    /**
     * 逻辑删除用户，并清理用户-角色关联
     *
     * @param id 用户 ID
     */
    void delete(Long id);

    /**
     * 全量覆盖分配角色
     *
     * @param id 用户 ID
     * @param roleIds 目标角色 ID 列表
     */
    void assignRoles(Long id, List<Long> roleIds);

    /**
     * 查询用户已分配的角色 ID 列表
     *
     * @param id 用户 ID
     * @return 用户已分配的角色 ID 列表
     */
    List<Long> listRoleIds(Long id);
}
