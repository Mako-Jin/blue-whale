package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.vo.UserDirectoryVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 用户目录应用服务：面向服务间调用的「用户存在且启用」批量判定、内网通讯录与按角色查用户。
 *
 * <p>仅供内网接口使用：{@code POST /user/v1/internal/exists} 供 IM 等业务服务在创建会话、
 * 添加群成员前校验目标用户是否真实存在；{@code GET /user/v1/internal/directory} 与
 * {@code GET /user/v1/internal/by-role} 供 OA 审批中心解析审批人。所有对外字段均不含口令等敏感信息。</p>
 *
 * @author BlueWhale
 */
public interface UserDirectoryAppService {

    /**
     * 批量查询启用状态的用户 ID。
     *
     * @param ids 待校验用户 ID（允许重复，内部去重）
     * @return 存在且启用的用户 ID 列表
     */
    List<Long> findEnabledIds(Collection<Long> ids);

    /**
     * 分页查询内网通讯录：仅启用且未删除的本租户用户，手机号与邮箱脱敏，部门名称批量补全。
     *
     * @param deptId 部门 ID（可空，为空时不过滤）
     * @param includeChildren 是否连同子部门一起查（{@code true} 时常选父部门也能看到下属成员）
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @param current 当前页码，小于 1 时按 1 处理
     * @param size 每页条数，小于 1 按 1 处理，超过 200 按 200 处理
     * @return 通讯录分页结果
     */
    PageResult<UserDirectoryVO> directory(Long deptId, boolean includeChildren, String keyword, long current, long size);

    /**
     * 按角色编码查询内网通讯录：角色不存在或已停用返回空列表，仅返回启用用户。
     *
     * @param roleCode 角色编码
     * @return 通讯录用户列表（按用户 ID 升序，最多 200 条）
     */
    List<UserDirectoryVO> byRole(String roleCode);
}
