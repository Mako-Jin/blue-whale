package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.SaveUserGroupCommand;
import com.bluewhale.auth.application.model.query.UserGroupPageQuery;
import com.bluewhale.auth.application.model.vo.UserGroupVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.List;

/**
 * 用户组应用服务（权限中心扩展 M3）。
 *
 * <p>静态组成员显式维护；动态组成员由规则求值快照重建，不可手工改。</p>
 *
 * @author BlueWhale
 */
public interface UserGroupAppService {

    /**
     * 新建用户组
     *
     * @param command 保存命令
     * @return 视图对象
     */
    UserGroupVO create(SaveUserGroupCommand command);

    /**
     * 更新用户组
     *
     * @param id 主键
     * @param command 保存命令
     * @return 视图对象
     */
    UserGroupVO update(Long id, SaveUserGroupCommand command);

    /**
     * 删除用户组（并清理成员关系）
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 查询用户组详情
     *
     * @param id 主键
     * @return 视图对象
     */
    UserGroupVO get(Long id);

    /**
     * 分页查询用户组
     *
     * @param query 分页查询
     * @return 分页结果
     */
    PageResult<UserGroupVO> page(UserGroupPageQuery query);

    /**
     * 全量替换静态组成员
     *
     * @param id 组 ID
     * @param memberUserIds 成员用户 ID 列表（空列表表示清空）
     */
    void replaceMembers(Long id, List<Long> memberUserIds);

    /**
     * 重算动态组成员（按 filters 规则求值并重建快照）
     *
     * @param id 组 ID
     * @return 命中成员数
     */
    long recomputeDynamicMembers(Long id);

    /**
     * 查询组成员用户 ID 列表
     *
     * @param id 组 ID
     * @return 成员用户 ID 列表
     */
    List<Long> listMemberUserIds(Long id);
}
