package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.CreateUserCommand;
import com.bluewhale.auth.application.model.command.UserLifecycleCommand;
import com.bluewhale.auth.application.model.vo.UserLifecycleVO;
import com.bluewhale.auth.domain.aggregate.UserAggregate;

/**
 * 人员生命周期应用服务：供 OA 人事异动审批通过后同步 Auth 账号状态与部门。
 *
 * <p>动作语义：入职建号或启用、转正保活、调岗改部门、离职停用（不删除）、返聘启用。
 * 停用走 {@link UserAggregate#disable()}，发布 {@code USER_DISABLED} 事件以撤销存量令牌并失效权限缓存；
 * 入职建号复用 {@link UserAppService#create(CreateUserCommand)}，一次性临时口令仅在本次响应返回。</p>
 *
 * @author BlueWhale
 */
public interface UserLifecycleAppService {

    /**
     * 执行人员生命周期动作。
     *
     * @param command 生命周期命令
     * @return 执行结果（入职建号时含一次性临时口令）
     */
    UserLifecycleVO apply(UserLifecycleCommand command);
}
