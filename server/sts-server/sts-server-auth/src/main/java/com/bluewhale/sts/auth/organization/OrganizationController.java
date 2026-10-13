package com.bluewhale.sts.auth.organization;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织接口 —— 组织树（部门树）的增删改查、移动，以及**成员归属与主岗**。
 *
 * <p><b>成员挂在组织下（{@code /orgs/{id}/members}），不单开平级资源。</b>
 * 与角色的两条关系边、用户组的成员关系是完全同一条判断：归属是组织的**内部边**，
 * 没有脱离组织独立存在的意义（不存在"没有组织的组织归属"），
 * URL 本身就说明了"改的是哪个组织的成员"，同时天然带上归属校验。
 *
 * <p>与用户组那两处有一处**形状上的差别**：成员路径里没有 {@code memberType} ——
 * {@code bw_auth_rel_org_user} 只装用户，层级由 {@code parent_id} 表达，
 * 没有多态列就不要在路径上假装有。
 *
 * <p><b>七个写方法各自标了 {@link Audited}，读方法都不标</b>（查询不产生审计）。
 * 四个动作在角色 / 用户组侧是分开的，这里同样，而且其中两个值得再强调一次：
 * <ul>
 *   <li>{@code PUT /orgs/{id}/parent}（移动）—— 一次改变**整棵子树上每个人的**数据范围
 *       （{@code DEPT_AND_CHILD} 按路径前缀求值），表面只是"换了个上级部门"。
 *       它有独立的动作标识 {@code auth.org.move}；</li>
 *   <li>{@code PUT /orgs/{id}/members/{userId}/primary}（设主岗）—— 唯一能
 *       **直接搬动一个人的数据范围**的操作：{@code DEPT} 取的就是主岗所在部门。
 *       它不改变任何计数，界面上只是多一个标记，所以更不能藏在一次 upsert 里。</li>
 * </ul>
 *
 * <p><b>路径写 {@code orgs}（短形）、权限码写 {@code auth:org:*}</b>，
 * 与 {@code user-groups} / {@code auth:user_group:*} 那处"两处不同形"是同一个约定的一部分：
 * URL 给人看，标识给程序与后台配置用，各有各的字符集要求。
 */
@RestController
@RequestMapping("/api/auth/orgs")
@Tag(name = "组织", description = "组织树的增删改查、移动，以及成员归属与主岗")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationMemberService organizationMemberService;

    public OrganizationController(OrganizationService organizationService,
                                  OrganizationMemberService organizationMemberService) {
        this.organizationService = organizationService;
        this.organizationMemberService = organizationMemberService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.ORG_READ)
    @Operation(summary = "分页查询组织",
            description = "按 keyword（编码 / 名称 / 全称） / status / orgType / parentId 过滤，"
                    + "按当前租户限定；parentId 取的是**直接下级**，要整棵子树请用树接口")
    public R<PageResult<OrganizationVO>> page(@ParameterObject OrganizationQuery query) {
        return R.ok(organizationService.page(query));
    }

    /**
     * 组织树。
     *
     * <p>放在 {@code /{id}} 之前是**为了可读性**而不是路由必需：Spring 对字面量路径
     * 的优先级高于变量，两个顺序都能正确匹配。写在这里是因为读代码的人
     * 会先怀疑这一条（"tree 会不会被当成 id"），把答案摆在眼前省一次查证。
     */
    @GetMapping("/tree")
    @RequiresPermission(AuthPermissions.ORG_READ)
    @Operation(summary = "查询组织树",
            description = "一次返回当前租户的整棵森林（**含已停用节点**，由界面置灰）；"
                    + "传 rootId 只看某一棵子树。父子不在结果集里的节点会被当成根返回（孤儿节点）")
    public R<List<OrganizationTreeNode>> tree(
            @RequestParam(required = false)
            @Parameter(description = "只看某棵子树时传它的主键；不传返回整个租户的森林")
            Long rootId) {
        return R.ok(organizationService.tree(rootId));
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.ORG_READ)
    @Operation(summary = "查询组织详情")
    public R<OrganizationVO> detail(@PathVariable @Parameter(description = "组织主键") Long id) {
        return R.ok(organizationService.detail(id));
    }

    @PostMapping
    @RequiresPermission(AuthPermissions.ORG_CREATE)
    @Audited(action = AuthAuditActions.ORG_CREATE, resource = AuthResources.ORG)
    @Operation(summary = "新增组织",
            description = "编码曾被已删除的组织占用时会恢复那一行，恢复出来的是**空部门**"
                    + "（名下的用户归属被清空）；orgLevel / codePath / fullName 由服务端按父级派生")
    public R<OrganizationVO> create(@Valid @RequestBody OrganizationSaveRequest request) {
        OrganizationVO created = organizationService.create(request);
        // 新建时主键是自增出来的，调用前并不存在，因此只能在业务跑完之后补
        AuditContext.target(created.getId());
        return R.ok(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.ORG_UPDATE)
    @Audited(action = AuthAuditActions.ORG_UPDATE, resource = AuthResources.ORG)
    @Operation(summary = "修改组织",
            description = "编码与所属租户不可修改；**层级也不在这里改** —— 请求里带了与原值不同的 "
                    + "parentId 会返回 40001 并指向移动接口。改名会重写整棵子树的 fullName")
    public R<OrganizationVO> update(@PathVariable @Parameter(description = "组织主键") Long id,
                                    @Valid @RequestBody OrganizationSaveRequest request) {
        OrganizationVO updated = organizationService.update(id, request);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @PutMapping("/{id}/parent")
    @RequiresPermission(AuthPermissions.ORG_UPDATE)
    @Audited(action = AuthAuditActions.ORG_MOVE, resource = AuthResources.ORG)
    @Operation(summary = "移动组织节点",
            description = "把整棵子树搬到另一个父级下（传空 body 表示提升为顶级）。"
                    + "会重写整棵子树的 codePath / orgLevel / fullName，"
                    + "因此**同时改变子树上每个人的 DEPT 数据范围**；挂到自己或自己的后代下会返回 40913")
    public R<OrganizationVO> move(@PathVariable @Parameter(description = "组织主键") Long id,
                                  @RequestBody OrganizationMoveRequest request) {
        OrganizationVO moved = organizationService.move(id, request);
        AuditContext.target(id);
        return R.ok(moved);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.ORG_DELETE)
    @Audited(action = AuthAuditActions.ORG_DELETE, resource = AuthResources.ORG)
    @Operation(summary = "删除组织",
            description = "仍有下级组织（40913）或仍有成员归属（40919）时拒绝；"
                    + "**没有级联删除**，也不提供「连人一起删」这种选项")
    public R<Void> delete(@PathVariable @Parameter(description = "组织主键") Long id) {
        organizationService.delete(id);
        AuditContext.target(id);
        return R.ok();
    }

    /* ------------------------------------------------------------------ 成员归属 */

    @GetMapping("/{id}/members")
    @RequiresPermission(AuthPermissions.ORG_READ)
    @Operation(summary = "分页查询组织成员",
            description = "可按 primaryOnly 只看主岗；成员名跨表解析，因此不提供按名搜索")
    public R<PageResult<OrgMemberVO>> pageMembers(
            @PathVariable @Parameter(description = "组织主键") Long id,
            @ParameterObject OrgMemberQuery query) {
        return R.ok(organizationMemberService.pageMembers(id, query));
    }

    @PostMapping("/{id}/members")
    @RequiresPermission(AuthPermissions.ORG_UPDATE)
    @Audited(action = AuthAuditActions.ORG_MEMBER_GRANT, resource = AuthResources.ORG)
    @Operation(summary = "把用户挂到组织下",
            description = "同一个组织与用户只保留一条有效归属，重复调用会改时间窗与岗位信息而不是堆行。"
                    + "不传 isPrimaryPosition 时：此人当前没有别的主岗则自动设为主岗，"
                    + "已有主岗则**保持不变**（绝不静默搬动数据范围）")
    public R<OrgMemberVO> bindMember(
            @PathVariable @Parameter(description = "组织主键") Long id,
            @Valid @RequestBody OrgMemberBindRequest request) {
        OrgMemberVO bound = organizationMemberService.bindMember(id, request);
        AuditContext.target(id);
        return R.ok(bound);
    }

    @PutMapping("/{id}/members/{userId}/primary")
    @RequiresPermission(AuthPermissions.ORG_UPDATE)
    @Audited(action = AuthAuditActions.ORG_SET_PRIMARY_POSITION, resource = AuthResources.ORG)
    @Operation(summary = "把某人的组织归属设为主岗",
            description = "**直接改变这个人的 DEPT 数据范围**，因此是独立接口与独立审计动作。"
                    + "会把该用户在本租户内的其他主岗全部降级（每人至多一个主岗）；"
                    + "要求先有归属，否则返回 40410")
    public R<OrgMemberVO> setPrimaryPosition(
            @PathVariable @Parameter(description = "组织主键") Long id,
            @PathVariable @Parameter(description = "用户主键") Long userId) {
        OrgMemberVO updated = organizationMemberService.setPrimaryPosition(id, userId);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @RequiresPermission(AuthPermissions.ORG_UPDATE)
    @Audited(action = AuthAuditActions.ORG_MEMBER_REVOKE, resource = AuthResources.ORG)
    @Operation(summary = "把用户移出组织",
            description = "本来就不在该组织下时返回 40410，不与移除成功混淆；"
                    + "移除主岗**不会自动把主岗转给别人** —— 转给谁是人事决定，"
                    + "此人此后暂时没有主岗，DEPT 档位往窄兜底成「仅本人」")
    public R<Void> revokeMember(
            @PathVariable @Parameter(description = "组织主键") Long id,
            @PathVariable @Parameter(description = "用户主键") Long userId) {
        organizationMemberService.revokeMember(id, userId);
        AuditContext.target(id);
        return R.ok();
    }

}
