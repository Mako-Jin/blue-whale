package com.bluewhale.sts.auth.user;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthAuditActions;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthResources;
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

/**
 * 用户接口。全部方法受权限保护，未认证 401、越权 403。
 *
 * <p><b>四个写方法都标了 {@link Audited}，两个读方法没标。</b>
 * 这不是遗漏：审计回答"谁对什么做了什么"，查询不改变任何东西，
 * 记下来只会把有价值的变更记录淹没在噪声里（而审计的价值恰恰建立在
 * "里面每一条都值得逐条看"之上）。将来若要记"谁看过敏感数据"，
 * 那是另一类需求（数据访问审计），该单独讨论，而不是顺手把 GET 也加上。
 *
 * <p><b>动作之间的区分是刻意做细的：</b>重置口令与修改资料共用
 * {@code auth:user:update} 权限，但审计动作分开（{@code auth.user.reset_password}
 * 与 {@code auth.user.update}）—— 前者是运维介入他人账号的动作，事后追责时
 * 与"改过资料"是完全不同的问题。
 */
@RestController
@RequestMapping("/api/auth/users")
@Tag(name = "用户", description = "用户（账号主体）的增删改查与口令重置")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

}
