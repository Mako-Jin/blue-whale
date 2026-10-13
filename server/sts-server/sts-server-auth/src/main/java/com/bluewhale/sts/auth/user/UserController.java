package com.bluewhale.sts.auth.user;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthAuditActions;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthResources;
import com.bluewhale.sts.auth.datascope.DataScopeService;
import com.bluewhale.sts.auth.datascope.UserDataScopeVO;
import com.bluewhale.sts.auth.organization.OrganizationMemberService;
import com.bluewhale.sts.auth.organization.UserOrgVO;
import com.bluewhale.sts.auth.role.RoleMemberService;
import com.bluewhale.sts.auth.role.UserRoleVO;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.basic.model.R;
import com.bluewhale.sts.components.audit.AuditContext;
import com.bluewhale.sts.components.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口。全部方法受权限保护，未认证 401、越权 403。
 *
 * <p><b>四个写方法都标了 {@link Audited}，读方法没标。</b>
 * 这不是遗漏：审计回答"谁对什么做了什么"，查询不改变任何东西，
 * 记下来只会把有价值的变更记录淹没在噪声里（而审计的价值恰恰建立在
 * "里面每一条都值得逐条看"之上）。将来若要记"谁看过敏感数据"，
 * 那是另一类需求（数据访问审计），该单独讨论，而不是顺手把 GET 也加上。
 *
 * <p><b>动作之间的区分是刻意做细的：</b>重置口令与修改资料共用
 * {@code auth:user:update} 权限，但审计动作分开（{@code auth.user.reset_password}
 * 与 {@code auth.user.update}）—— 前者是运维介入他人账号的动作，事后追责时
 * 与"改过资料"是完全不同的问题。
 *
 * <p><b>{@code GET /{id}/roles} 是本类唯一一条"读别的域"的接口</b>
 * （由 {@code RoleMemberService} 实现），挂在这里而不是 {@code /api/auth/roles} 下：
 * 路径的主语是"这个用户"，问的是"他身上的角色是哪来的"。
 * 反向从 {@code /api/auth/roles/{id}/members} 进来问的是另一个问题
 * （"这个角色给了谁"），两者不是同一张视图的两个入口，而是两个问题 ——
 * 因此各自归属自己的主语，哪怕底层查的是同一张关系表。
 */
@RestController
@RequestMapping("/api/auth/users")
@Tag(name = "用户", description = "用户（账号主体）的增删改查与口令重置")
public class UserController {

    private final UserService userService;
    private final RoleMemberService roleMemberService;
    private final DataScopeService dataScopeService;
    private final OrganizationMemberService organizationMemberService;

    public UserController(UserService userService, RoleMemberService roleMemberService,
                          DataScopeService dataScopeService,
                          OrganizationMemberService organizationMemberService) {
        this.userService = userService;
        this.roleMemberService = roleMemberService;
        this.dataScopeService = dataScopeService;
        this.organizationMemberService = organizationMemberService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.USER_READ)
    @Operation(summary = "分页查询用户", description = "默认限定为当前登录用户所属租户")
    public R<PageResult<UserVO>> page(@ParameterObject UserQuery query) {
        return R.ok(userService.page(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_READ)
    @Operation(summary = "查询用户详情")
    public R<UserVO> detail(@PathVariable @Parameter(description = "用户主键") Long id) {
        return R.ok(userService.detail(id));
    }

    @PostMapping
    @RequiresPermission(AuthPermissions.USER_CREATE)
    @Audited(action = AuthAuditActions.USER_CREATE, resource = AuthResources.USER)
    @Operation(summary = "新增用户", description = "口令必填；登录名在租户内唯一")
    public R<UserVO> create(@Valid @RequestBody UserSaveRequest request) {
        UserVO created = userService.create(request);
        // 审计的"对象"只能在业务跑完之后才拿得到：新建时主键是自增出来的，调用前并不存在。
        // 因此这里是拦截器与业务代码的一次分工 —— 拦截器知道"谁"和"做了什么"，
        // 业务代码负责回答"动的是哪一条"（见 AuditContext 的注释）。
        AuditContext.target(created.getId());
        return R.ok(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_UPDATE)
    @Audited(action = AuthAuditActions.USER_UPDATE, resource = AuthResources.USER)
    @Operation(summary = "修改用户", description = "登录名与所属租户不可修改")
    public R<UserVO> update(@PathVariable @Parameter(description = "用户主键") Long id,
                            @Valid @RequestBody UserSaveRequest request) {
        UserVO updated = userService.update(id, request);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @PostMapping("/{id}/password")
    @RequiresPermission(AuthPermissions.USER_UPDATE)
    @Audited(action = AuthAuditActions.USER_RESET_PASSWORD, resource = AuthResources.USER)
    @Operation(summary = "重置口令", description = "重置后强制该用户下次登录修改口令，并解除锁定")
    public R<Void> resetPassword(@PathVariable @Parameter(description = "用户主键") Long id,
                                 @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.getPassword());
        // 只记"重置了谁的"这个事实。**口令本身绝不进审计**：审计表的保留期远长于
        // 任何口令的寿命，写进去等于把一次性凭证永久留在库里，而这正是重置口令要消除的东西。
        AuditContext.target(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_DELETE)
    @Audited(action = AuthAuditActions.USER_DELETE, resource = AuthResources.USER)
    @Operation(summary = "删除用户", description = "不允许删除当前登录用户")
    public R<Void> delete(@PathVariable @Parameter(description = "用户主键") Long id) {
        userService.delete(id);
        AuditContext.target(id);
        return R.ok();
    }

    /**
     * 反查该用户身上的角色及其来源。
     *
     * <p>不标 {@link Audited}：它是查询（理由见类注释）。
     * 权限用 {@code auth:user:read} —— 它读的是"某个用户"的信息，
     * 与列表 / 详情同类，理由见 {@code RoleMemberService.rolesOfUser}。
     */
    @GetMapping("/{id}/roles")
    @RequiresPermission(AuthPermissions.USER_READ)
    @Operation(summary = "反查用户的角色来源",
            description = "列出该用户直接持有的角色与经用户组持有的角色；"
                    + "**停用的角色 / 停用的组 / 已过期的授权照原样返回**，"
                    + "因为它们正是「为什么没有权限」的答案")
    public R<List<UserRoleVO>> roles(@PathVariable @Parameter(description = "用户主键") Long id) {
        return R.ok(roleMemberService.rolesOfUser(id));
    }

    /**
     * 反查该用户的数据范围。
     *
     * <p>与 {@code /{id}/roles} 同属"反查某个人"这一类，因此同样归
     * {@code auth:user:read}、同样不标 {@link Audited}。差别在于它回答的是
     * "他身上的角色合起来能看到多大范围"—— 一个由多个角色的 {@code data_scope}
     * 合并出来的结果。
     *
     * <p><b>返回里带一个 {@code precise} 标记，而不是只返回档位。</b>因为
     * "配了 {@code DEPT} 但系统还不会算"与"就是 {@code ALL}"是两件完全不同的事，
     * 而只返回一个字符串的做法让它们长得一模一样。
     *
     * <p><b>组织归属落地之后，这个接口从"能说的变多了"这件事上受益最多：</b>
     * {@code DEPT} 系列不再只能是 {@code precise = false} —— 该用户有一个有效主岗时，
     * 返回里会带上主岗组织与**可见组织集合**（见 {@code UserDataScopeVO}）。
     * 同时它多了一种失败原因：有主岗但部门被停用，或归属已过期 ——
     * 这些都会让 {@code precise} 落回 false，而 {@code note} 会写明是哪一种。
     */
    @GetMapping("/{id}/data-scope")
    @RequiresPermission(AuthPermissions.USER_READ)
    @Operation(summary = "反查用户的数据范围",
            description = "合并该用户全部生效角色的 data_scope（取最宽的一档）。"
                    + "**precise 为 false 表示该档位尚不能被精确求值**"
                    + "（DEPT / DEPT_AND_CHILD 需要该用户有一个有效主岗，"
                    + "CUSTOM 需要解析 filter_json），此时已接入的查询保守地按「仅本人」处理；"
                    + "为 true 时会带上主岗组织与可见组织集合")
    public R<UserDataScopeVO> dataScope(@PathVariable @Parameter(description = "用户主键") Long id) {
        return R.ok(dataScopeService.resolve(id));
    }

    /**
     * 反查该用户所属的组织。
     *
     * <p>与 {@code /{id}/roles}、{@code /{id}/data-scope} 同属"反查某个人"这一类，
     * 挂在这个主语下而不是 {@code /api/auth/orgs} 下：路径的主语是"这个用户"，
     * 问的是"他在哪些部门"。反向从 {@code /api/auth/orgs/{id}/members} 进来问的是
     * 另一个问题（"这个部门里有谁"），两者不是同一张视图的两个入口。
     *
     * <p>权限归 {@code auth:user:read} 而不是 {@code auth:org:read}，
     * 与 {@code /{id}/roles} 归 {@code auth:user:read} 是同一条：
     * 它读的是"某个用户"的信息。这条选择有一个可验证的后果 ——
     * 只有 {@code org:read} 的人能改组织结构，但看不到"某个人在哪些部门"；
     * 反之，只有 {@code user:read} 的人能反查个人归属，却看不到组织树本身。
     * 两者都是"信息不足"而不是越权，方向是安全的。
     */
    @GetMapping("/{id}/organizations")
    @RequiresPermission(AuthPermissions.USER_READ)
    @Operation(summary = "反查用户的组织归属",
            description = "列出该用户在各组织下的归属（含非主岗与已过期的），"
                    + "每行带 effectiveNow 与 orgStatus —— 排查"
                    + "「他配了本部门范围、为什么还是只看得到自己」时，答案通常就在这两列里")
    public R<List<UserOrgVO>> organizations(
            @PathVariable @Parameter(description = "用户主键") Long id) {
        return R.ok(organizationMemberService.listByUser(id));
    }

}
