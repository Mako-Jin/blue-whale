package com.bluewhale.sts.auth.usergroup;

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
 * 用户组接口 —— 用户组的增删改查，以及**把用户加进组 / 移出组**。
 *
 * <p><b>组成员挂在用户组下（{@code /user-groups/{id}/members}），不单开平级资源。</b>
 * 与角色的两条关系边（{@code /roles/{id}/resources} 与 {@code /roles/{id}/members}）
 * 是完全同一条判断：成员关系是用户组的**内部边**，没有脱离组独立存在的意义，
 * 也没有独立生命周期（不存在"没有用户组的成员"）。挂在组下，URL 本身就说明了
 * "改的是哪个组的成员"，同时天然带上归属校验 —— {@code {id}} 是必经的一环。
 *
 * <p><b>三个写用户组的方法与两个改成员关系的方法都标了 {@link Audited}，读方法没标</b>
 * （查询不产生审计，理由见 {@code UserController} 的类注释）。
 * 四个关系动作在角色侧是四个独立标识，这里同样：
 * "把用户加进这个组"与"改这个组名"在事后追责时是完全不同的问题 ——
 * 前者可能是一次**间接提权**（组已被授予角色时），见
 * {@code AuthAuditActions.USER_GROUP_MEMBER_GRANT}。
 *
 * <p><b>路径写 {@code user-groups}（连字符），权限码写 {@code auth:user_group:*}（下划线）</b>——
 * 两处不同形是刻意的，理由见 {@code AuthResources.USER_GROUP}。
 */
@RestController
@RequestMapping("/api/auth/user-groups")
@Tag(name = "用户组", description = "用户组的增删改查与组成员关系")
public class UserGroupController {

    private final UserGroupService userGroupService;

    public UserGroupController(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.USER_GROUP_READ)
    @Operation(summary = "分页查询用户组",
            description = "按 keyword（编码 / 名称） / category / enabled / parentId 过滤，按当前租户限定")
    public R<PageResult<UserGroupVO>> page(@ParameterObject UserGroupQuery query) {
        return R.ok(userGroupService.page(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_GROUP_READ)
    @Operation(summary = "查询用户组详情")
    public R<UserGroupVO> detail(@PathVariable @Parameter(description = "用户组主键") Long id) {
        return R.ok(userGroupService.detail(id));
    }

    @PostMapping
    @RequiresPermission(AuthPermissions.USER_GROUP_CREATE)
    @Audited(action = AuthAuditActions.USER_GROUP_CREATE, resource = AuthResources.USER_GROUP)
    @Operation(summary = "新增用户组",
            description = "编码曾被已删除的用户组占用时会恢复那一行，且恢复出来的是空组（无成员、无角色）")
    public R<UserGroupVO> create(@Valid @RequestBody UserGroupSaveRequest request) {
        UserGroupVO created = userGroupService.create(request);
        // 新建时主键是自增出来的，调用前并不存在，因此只能在业务跑完之后补
        AuditContext.target(created.getId());
        return R.ok(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_GROUP_UPDATE)
    @Audited(action = AuthAuditActions.USER_GROUP_UPDATE, resource = AuthResources.USER_GROUP)
    @Operation(summary = "修改用户组",
            description = "编码与所属租户不可修改；**isEnabled 是真开关**，停用后该组立刻不再产生权限")
    public R<UserGroupVO> update(@PathVariable @Parameter(description = "用户组主键") Long id,
                                 @Valid @RequestBody UserGroupSaveRequest request) {
        UserGroupVO updated = userGroupService.update(id, request);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.USER_GROUP_DELETE)
    @Audited(action = AuthAuditActions.USER_GROUP_DELETE, resource = AuthResources.USER_GROUP)
    @Operation(summary = "删除用户组",
            description = "仍有成员（40918）或有子组（40913）时拒绝；该组自己持有的角色会被一并撤销")
    public R<Void> delete(@PathVariable @Parameter(description = "用户组主键") Long id) {
        userGroupService.delete(id);
        AuditContext.target(id);
        return R.ok();
    }

    /* ------------------------------------------------------------------ 组成员关系 */

    @GetMapping("/{id}/members")
    @RequiresPermission(AuthPermissions.USER_GROUP_READ)
    @Operation(summary = "分页查询组成员",
            description = "可按 memberType 过滤；成员名跨表解析，因此不提供按名搜索")
    public R<PageResult<UserGroupMemberVO>> pageMembers(
            @PathVariable @Parameter(description = "用户组主键") Long id,
            @ParameterObject UserGroupMemberQuery query) {
        return R.ok(userGroupService.pageMembers(id, query));
    }

    /**
     * 把一个用户加进用户组。
     *
     * <p>权限码复用 {@code auth:user_group:update}，与角色侧一致 ——
     * 理由与取舍见 {@code AuthPermissions.USER_GROUP_UPDATE} 的注释。
     * 但这条路径的性质值得再记一次：当这个组**已经被授予角色**时，
     * 这一调用就是一次**间接提权** —— 受益者当下就换了，
     * 而没有任何一条记录写着"谁给这个用户提了权"，所以审计动作必须独立。
     */
    @PostMapping("/{id}/members")
    @RequiresPermission(AuthPermissions.USER_GROUP_UPDATE)
    @Audited(action = AuthAuditActions.USER_GROUP_MEMBER_GRANT, resource = AuthResources.USER_GROUP)
    @Operation(summary = "把用户加进用户组",
            description = "memberType **当前只接受 USER**（组的嵌套在判定里不生效，传 GROUP 返回 40001）；"
                    + "同一个组与用户只保留一条有效关系，重复调用会改时间窗而不是堆行")
    public R<UserGroupMemberVO> bindMember(
            @PathVariable @Parameter(description = "用户组主键") Long id,
            @Valid @RequestBody UserGroupMemberBindRequest request) {
        UserGroupMemberVO bound = userGroupService.bindMember(id, request);
        AuditContext.target(id);
        return R.ok(bound);
    }

    /**
     * 把一个用户移出用户组。
     *
     * <p>路径里 {@code memberType} 与 {@code memberId} 两段都不能少 ——
     * 理由见 {@code UserGroupService.revokeMember}：{@code member_id} 是多态列，
     * 主键在两个自增序列之间会撞号，少一段就会移除错对象，
     * 而"移错了"表现为权限凭空消失，很难回查。
     */
    @DeleteMapping("/{id}/members/{memberType}/{memberId}")
    @RequiresPermission(AuthPermissions.USER_GROUP_UPDATE)
    @Audited(action = AuthAuditActions.USER_GROUP_MEMBER_REVOKE, resource = AuthResources.USER_GROUP)
    @Operation(summary = "把用户移出用户组",
            description = "memberType 取 USER / GROUP；本来就不在组里时返回 40409，不与移除成功混淆")
    public R<Void> revokeMember(
            @PathVariable @Parameter(description = "用户组主键") Long id,
            @PathVariable @Parameter(description = "成员类型：USER / GROUP") String memberType,
            @PathVariable @Parameter(description = "成员主键") Long memberId) {
        userGroupService.revokeMember(id, memberType, memberId);
        AuditContext.target(id);
        return R.ok();
    }

}
