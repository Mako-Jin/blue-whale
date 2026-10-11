package com.bluewhale.sts.auth.user;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.basic.model.R;
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

/** 用户接口。全部方法受权限保护，未认证 401、越权 403。 */
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
    @Operation(summary = "新增用户", description = "口令必填；登录名在租户内唯一")
    public R<UserVO> create(@Valid @RequestBody UserSaveRequest request) {
        return R.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_UPDATE)
    @Operation(summary = "修改用户", description = "登录名与所属租户不可修改")
    public R<UserVO> update(@PathVariable @Parameter(description = "用户主键") Long id,
                            @Valid @RequestBody UserSaveRequest request) {
        return R.ok(userService.update(id, request));
    }

    @PostMapping("/{id}/password")
    @RequiresPermission(AuthPermissions.USER_UPDATE)
    @Operation(summary = "重置口令", description = "重置后强制该用户下次登录修改口令，并解除锁定")
    public R<Void> resetPassword(@PathVariable @Parameter(description = "用户主键") Long id,
                                 @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.getPassword());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_DELETE)
    @Operation(summary = "删除用户", description = "不允许删除当前登录用户")
    public R<Void> delete(@PathVariable @Parameter(description = "用户主键") Long id) {
        userService.delete(id);
        return R.ok();
    }

}
