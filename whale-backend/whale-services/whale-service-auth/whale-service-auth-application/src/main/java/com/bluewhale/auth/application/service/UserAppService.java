package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.AssignUserRolesCommand;
import com.bluewhale.auth.application.model.command.ChangeUserStatusCommand;
import com.bluewhale.auth.application.model.command.CreateUserCommand;
import com.bluewhale.auth.application.model.command.UpdateUserCommand;
import com.bluewhale.auth.application.model.query.UserPageQuery;
import com.bluewhale.auth.application.model.vo.ResetPasswordVO;
import com.bluewhale.auth.application.model.vo.UserVO;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.role.repository.RoleRepository;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import java.util.List;

/**
 * 用户管理应用服务：编排用户聚合与用户-角色关联的管理端用例（REQ-ARCH-001 / T-DDD-9）。
 *
 * <p>依赖方向遵循 DDD 四层约定：仅依赖领域层聚合/值对象/仓储端口、口令加密器与领域事件发布端口，
 * 不依赖 {@code entity}/{@code mapper}/{@code infrastructure}/{@code service}。返回视图对象或
 * {@link PageResult}，不向调用方泄漏领域对象与持久化对象。</p>
 *
 * <p>语义与旧 {@code SysUserServiceImpl} 逐条对齐：同样的校验顺序、异常文案与业务码；
 * 启停 / 删除 / 角色变更等写操作改发领域事件，由基础设施层监听器在事务提交后失效用户权限缓存；
 * 事务边界仅放在多写方法上。</p>
 *
 * @author BlueWhale
 */
public interface UserAppService {

    /**
     * 分页查询用户（关键字模糊匹配用户名/昵称/手机号，按创建时间降序）
     *
     * @param query 用户分页查询条件
     * @return 用户分页结果
     */
    public PageResult<UserVO> page(UserPageQuery query);

    /**
     * 查询用户详情
     *
     * @param id 用户 ID
     * @return 用户视图对象
     */
    public UserVO getDetail(Long id);

    /**
     * 创建用户：用户名值对象前置校验 → 唯一性校验 → 口令策略校验 → BCrypt 加密 → 保存
     *
     * <p>按策略默认值，管理员创建账号后目标用户首次登录即被强制改密（REQ-SEC-023）；
     * 初始口令密文同时写入口令历史，避免用户把初始口令改回去。</p>
     *
     * @param command 创建用户命令
     * @return 新建用户视图对象
     */
    public UserVO create(CreateUserCommand command);

    /**
     * 更新用户：口令非空时按策略重置，其余资料字段全量覆盖，状态非空时变更
     *
     * <p>管理员代改口令后按策略置强制首登改密标记（{@code whale.auth.password.force-change-on-create}），
     * 使目标用户需在下次登录后自行设置新口令；新密文写入口令历史并发布改密事件撤销存量令牌。</p>
     *
     * @param id 用户 ID
     * @param command 更新用户命令
     * @return 更新后的用户视图对象
     */
    public UserVO update(Long id, UpdateUserCommand command);

    /**
     * 管理员重置口令：系统生成一次性随机临时口令（REQ-SEC-023）
     *
     * <p>临时口令仅在本次响应中返回，服务端只保存 BCrypt 密文；重置后置强制首登改密标记，
     * 目标用户下次登录必须先自行修改口令。事件发布后基础设施层会撤销该用户全部存量令牌。</p>
     *
     * @param id 用户 ID
     * @return 重置结果（含一次性临时口令）
     */
    public ResetPasswordVO resetPassword(Long id);

    /**
     * 变更用户状态：校验目标状态合法后保存，并发布状态变更事件
     *
     * @param id 用户 ID
     * @param command 变更状态命令
     */
    public void changeStatus(Long id, ChangeUserStatusCommand command);

    /**
     * 删除用户：逻辑删除用户并清理用户-角色关联，随后发布删除事件
     *
     * @param id 用户 ID
     */
    public void delete(Long id);

    /**
     * 全量覆盖用户角色：校验角色有效性后重建关联，并发布角色分配事件
     *
     * @param id 用户 ID
     * @param command 分配角色命令
     */
    public void assignRoles(Long id, AssignUserRolesCommand command);

    /**
     * 查询用户已分配的角色 ID 列表
     *
     * @param id 用户 ID
     * @return 角色 ID 列表
     */
    public List<Long> listRoleIds(Long id);
}
