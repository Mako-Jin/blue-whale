package com.bluewhale.sts.auth.role;

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

import java.util.List;

/**
 * 角色接口 —— 角色的增删改查，以及**把资源授给角色 / 撤销授权**、
 * **把角色授给成员 / 解除成员关系**。
 *
 * <p><b>两条关系边都挂在角色下（{@code /roles/{id}/resources} 与
 * {@code /roles/{id}/members}），不单开 {@code /role-resources} 一类的平级资源。</b>
 * 理由是关系是角色的**内部边**：授权关系没有脱离角色与资源独立存在的意义，
 * 成员关系也没有脱离角色与成员独立存在的意义，两者都没有独立的生命周期
 * （不存在"无角色的授权"，也不存在"无角色的成员"）。挂在角色下，URL 本身就说明了
 * "改的是这个角色的哪一部分"，同时天然带上了归属校验 —— {@code {id}} 是必经的一环。
 *
 * <p><b>撤销的路径用被授对象的标识，而不是关系行的 {@code grantId}。</b>
 * 资源侧用 {@code resourceId}：调用方（授权界面）手里有两个东西 ——
 * 资源树（给主键）和已授列表（给主键 + 关系主键），用资源主键可以让"授"与"撤"完全对称，
 * 而 {@code grantId} 会随 upsert 换行、随复活换 id，写进路径会让调用方必须记住一行状态。
 * 成员侧同理，但要多带一段 {@code memberType}：{@code member_id} 只在它自己所指向的
 * 那张表里有意义（用户与用户组是两个独立的自增序列，会撞号），
 * 只用 {@code memberId} 定位会让"撤销给了 ID=5 的用户"连带撤掉"ID=5 的用户组"。
 * 三段都比两段更好，是因为这里的两段**真的是两段**。
 *
 * <p>两个写角色的方法与四个改关系的方法都标了 {@link Audited}，读方法没标：
 * 查询不产生审计（理由见 {@code UserController} 的类注释）。
 * <b>四个关系动作是四个独立标识</b>，虽然统统共用 {@code auth:role:update} 权限码 ——
 * "把资源授给角色"与"把角色授给人"在事后追责时是两个完全不同的问题，
 * 见 {@code AuthAuditActions.ROLE_MEMBER_GRANT} 的注释。
 */
@RestController
@RequestMapping("/api/auth/roles")
@Tag(name = "角色", description = "角色的增删改查、角色资源授权与角色成员关系")
public class RoleController {

    private final RoleService roleService;
    private final RoleMemberService roleMemberService;

    public RoleController(RoleService roleService, RoleMemberService roleMemberService) {
        this.roleService = roleService;
        this.roleMemberService = roleMemberService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.ROLE_READ)
    @Operation(summary = "分页查询角色")
    public R<PageResult<RoleVO>> page(@ParameterObject RoleQuery query) {
        return R.ok(roleService.page(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.ROLE_READ)
    @Operation(summary = "查询角色详情")
    public R<RoleVO> detail(@PathVariable @Parameter(description = "角色主键") Long id) {
        return R.ok(roleService.detail(id));
    }

    @PostMapping
    @RequiresPermission(AuthPermissions.ROLE_CREATE)
    @Audited(action = AuthAuditActions.ROLE_CREATE, resource = AuthResources.ROLE)
    @Operation(summary = "新增角色", description = "编码曾被已删除的角色占用时会复活那一行")
    public R<RoleVO> create(@Valid @RequestBody RoleSaveRequest request) {
        RoleVO created = roleService.create(request);
        // 新建时主键是自增出来的，调用前并不存在，因此只能在业务跑完之后补
        AuditContext.target(created.getId());
        return R.ok(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_UPDATE, resource = AuthResources.ROLE)
    @Operation(summary = "修改角色", description = "角色编码与所属租户不可修改；系统内置角色不可停用")
    public R<RoleVO> update(@PathVariable @Parameter(description = "角色主键") Long id,
                            @Valid @RequestBody RoleSaveRequest request) {
        RoleVO updated = roleService.update(id, request);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.ROLE_DELETE)
    @Audited(action = AuthAuditActions.ROLE_DELETE, resource = AuthResources.ROLE)
    @Operation(summary = "删除角色",
            description = "系统内置角色、有子角色、有成员时拒绝删除；角色自身的授权会被一并撤销")
    public R<Void> delete(@PathVariable @Parameter(description = "角色主键") Long id) {
        roleService.delete(id);
        AuditContext.target(id);
        return R.ok();
    }

    @GetMapping("/{id}/resources")
    @RequiresPermission(AuthPermissions.ROLE_READ)
    @Operation(summary = "查询角色已授予的资源")
    public R<List<RoleResourceVO>> listGrants(@PathVariable @Parameter(description = "角色主键") Long id) {
        return R.ok(roleService.listGrants(id));
    }

    @PostMapping("/{id}/resources")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_GRANT, resource = AuthResources.ROLE)
    @Operation(summary = "给角色授予资源",
            description = "同一个角色与资源只保留一条有效授权；重复调用会改效果而不是堆行")
    public R<RoleResourceVO> grant(@PathVariable @Parameter(description = "角色主键") Long id,
                                   @Valid @RequestBody RoleResourceGrantRequest request) {
        RoleResourceVO granted = roleService.grant(id, request);
        AuditContext.target(id);
        return R.ok(granted);
    }

    @DeleteMapping("/{id}/resources/{resourceId}")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_REVOKE, resource = AuthResources.ROLE)
    @Operation(summary = "撤销角色的资源授权", description = "从未授予过的资源返回 40407，不与撤销成功混淆")
    public R<Void> revoke(@PathVariable @Parameter(description = "角色主键") Long id,
                          @PathVariable @Parameter(description = "资源主键") Long resourceId) {
        roleService.revoke(id, resourceId);
        AuditContext.target(id);
        return R.ok();
    }

    /* ------------------------------------------------------------------ 成员关系 */

    @GetMapping("/{id}/members")
    @RequiresPermission(AuthPermissions.ROLE_READ)
    @Operation(summary = "分页查询角色成员",
            description = "可按 memberType 过滤；成员名跨两张表解析，因此不提供按名搜索")
    public R<PageResult<RoleMemberVO>> pageMembers(@PathVariable @Parameter(description = "角色主键") Long id,
                                                   @ParameterObject RoleMemberQuery query) {
        return R.ok(roleMemberService.page(id, query));
    }

    /**
     * 把角色授给一个成员（用户或用户组）。
     *
     * <p>权限码复用 {@code auth:role:update}，与授权接口一致 —— 理由与取舍见
     * {@code AuthPermissions.ROLE_UPDATE} 的注释：目前只有一个身份持有它，
     * 拆出一个 {@code auth:role:member} 会是一个没人能"不同地持有"的权限码。
     * 但这条路径的性质值得记下：把资源授给角色，你得先持有那个角色才谈得上受益；
     * 把角色授给一个人（包括自己），受益者当下就换了。这是当前唯一的**直接提权**路径，
     * 一旦出现第二个持有 {@code auth:role:update} 但不持有全部权限的角色，就必须拆。
     */
    @PostMapping("/{id}/members")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_MEMBER_GRANT, resource = AuthResources.ROLE)
    @Operation(summary = "给角色授予成员",
            description = "memberType 必填（USER / GROUP）；同一个角色与成员只保留一条有效关系，"
                    + "重复调用会改时间窗而不是堆行")
    public R<RoleMemberVO> bindMember(@PathVariable @Parameter(description = "角色主键") Long id,
                                      @Valid @RequestBody RoleMemberBindRequest request) {
        RoleMemberVO bound = roleMemberService.bind(id, request);
        AuditContext.target(id);
        return R.ok(bound);
    }

    /**
     * 解除一段成员关系。
     *
     * <p>路径里 {@code memberType} 与 {@code memberId} 两段都不能少，
     * 理由见类注释：主键在两个自增序列之间会撞号，
     * 少一段就会撤错对象，而"撤错了"表现为权限凭空消失，很难回查。
     */
    @DeleteMapping("/{id}/members/{memberType}/{memberId}")
    @RequiresPermission(AuthPermissions.ROLE_UPDATE)
    @Audited(action = AuthAuditActions.ROLE_MEMBER_REVOKE, resource = AuthResources.ROLE)
    @Operation(summary = "解除角色的成员关系",
            description = "memberType 取 USER / GROUP；从未授过的成员返回 40408，不与解除成功混淆")
    public R<Void> revokeMember(@PathVariable @Parameter(description = "角色主键") Long id,
                                @PathVariable @Parameter(description = "成员类型：USER / GROUP") String memberType,
                                @PathVariable @Parameter(description = "成员主键") Long memberId) {
        roleMemberService.revoke(id, memberType, memberId);
        AuditContext.target(id);
        return R.ok();
    }

}
