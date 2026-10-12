package com.bluewhale.sts.auth.role;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.DataScope;
import com.bluewhale.sts.auth.common.enums.GrantEffect;
import com.bluewhale.sts.auth.common.enums.ResourceType;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.relation.RelRoleMemberEntity;
import com.bluewhale.sts.auth.relation.RelRoleMemberMapper;
import com.bluewhale.sts.auth.relation.RelRoleResourceEntity;
import com.bluewhale.sts.auth.relation.RelRoleResourceMapper;
import com.bluewhale.sts.auth.resource.ResourceEntity;
import com.bluewhale.sts.auth.resource.ResourceMapper;
import com.bluewhale.sts.auth.tenant.TenantMapper;
import com.bluewhale.sts.common.basic.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色应用服务 —— 角色的增删改查，以及**把资源授给角色**。
 *
 * <p><b>本切片补上的是 RBAC 判定的最后一块可配置环节。</b>在此之前：
 * 资源目录能"造权限码"（ADR-0021），用户可以登录、也能拿到判定结果，
 * 但**没有任何接口能把资源授给角色** —— 管理员那批权限全部来自
 * {@code DefaultAdminSeeder} 的硬编码。也就是说权限体系的形状是固定的，
 * 改一次授权只能改库。有了本类，"给角色配权限"才成为一个可审计的接口调用。
 *
 * <p><b>约束比用户 / 租户更紧，因为改的是判定依据本身：</b>
 * <ul>
 *   <li><b>角色编码不可修改</b>（{@code role_code}）：它是判定链路之外仍被引用的
 *       稳定标识，改了等于让 {@code @RequiresPermission} 之外的一切引用失效。
 *       与 {@code TenantService} 对租户编码的处理一致。</li>
 *   <li><b>删除有三道拒绝</b>：系统内置角色、有子角色、有成员。前两条顺序在前，
 *       因为它们描述的是"这个角色本身不能走"，而"有成员"是"还有人靠它"。详见 {@link #delete}。</li>
 *   <li><b>系统内置角色不可停用、不可改分类</b>。后者看似多余，其实是**堵住一条绕过**：
 *       若允许把 {@code category} 从 {@code SYSTEM} 改成 {@code CUSTOM}，
 *       就可以先改分类、再删除 —— 两道保护会被一次 update 绕开。</li>
 *   <li><b>授权是 upsert</b>：同一个 (角色, 资源) 只保留一条有效行。既支持"改效果"
 *       （ALLOW → DENY），也顺带清掉历史遗留的重复行 —— 重复行会让旧的 DENY 复活，
 *       把一次授权变成静默的拒绝。详见 {@link #grant}。</li>
 * </ul>
 *
 * <p><b>"谁有这个角色"不在本类。</b>那是角色成员关系
 * （{@code bw_auth_rel_role_member}），落在 {@link RoleMemberService} 里 ——
 * 它与本类共用一张"角色"但没有任何共同逻辑：本类只回答"这个角色有哪些权限"，
 * 不回答"谁有这个角色"。两者在 {@code RoleController} 上是同一个 URI 下的
 * {@code /resources} 与 {@code /members} 两段，边界与 {@code /resources} 与
 * {@code /members} 的划分一致。
 */
@Service
public class RoleService {

    /** 系统内置角色的分类值。与 {@code DefaultAdminSeeder} 写入的 {@code category} 一致 */
    private static final String CATEGORY_SYSTEM = "SYSTEM";

    /** 只存不用的层级列默认值 */
    private static final int DEFAULT_ROLE_LEVEL = 1;

    private final RoleMapper roleMapper;
    private final RelRoleResourceMapper relRoleResourceMapper;
    private final RelRoleMemberMapper relRoleMemberMapper;
    private final ResourceMapper resourceMapper;
    private final TenantMapper tenantMapper;

    public RoleService(RoleMapper roleMapper,
                       RelRoleResourceMapper relRoleResourceMapper,
                       RelRoleMemberMapper relRoleMemberMapper,
                       ResourceMapper resourceMapper,
                       TenantMapper tenantMapper) {
        this.roleMapper = roleMapper;
        this.relRoleResourceMapper = relRoleResourceMapper;
        this.relRoleMemberMapper = relRoleMemberMapper;
        this.resourceMapper = resourceMapper;
        this.tenantMapper = tenantMapper;
    }

    /* ------------------------------------------------------------------ 角色 CRUD */

    @Transactional(readOnly = true)
    public PageResult<RoleVO> page(RoleQuery query) {
        // 过滤值先取局部变量：`eq(condition, 列, 值)` 的"值"是无条件求值的，
        // 写成 `query.getKeyword().trim()` 会在 keyword 为空时当场 NPE。
        // 这条坑在 ResourceService.page 上已经踩过一次，见那里的长注释。
        String keyword = normalize(query.getKeyword());
        String status = normalizeStatusFilter(query.getStatus());
        String category = normalize(query.getCategory());
        Long tenantId = resolveTenantId(query.getTenantId());

        var wrapper = Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getTenantId, tenantId)
                .and(keyword != null, w -> w
                        .like(RoleEntity::getRoleCode, keyword)
                        .or().like(RoleEntity::getRoleName, keyword))
                .eq(status != null, RoleEntity::getStatus, status)
                .eq(category != null, RoleEntity::getCategory, category)
                // 先 sortNo 再 id：只按 sortNo 排时，多条同序记录在不同页之间的相对顺序
                // 由数据库决定，"翻页时某条记录出现两次或一次都不出现"就是这么来的
                .orderByAsc(RoleEntity::getSortNo)
                .orderByAsc(RoleEntity::getId);

        Page<RoleEntity> page = roleMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<RoleVO> records = page.getRecords().stream().map(RoleService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public RoleVO detail(Long id) {
        return toVO(requireRole(id));
    }

    /**
     * 新建角色。
     *
     * <p><b>核心是"编码被一条已删除的角色占着"这一支。</b>
     * 唯一索引 {@code uk_bw_auth_role_tenant_code} 建在 {@code (tenant_id, role_code)} 上、
     * **不含 {@code deleted}**，而 MyBatis-Plus 给所有自动查询追加 {@code deleted = 0} ——
     * 用常规查询判重会**看不见已删除的行**，判重通过后 insert 撞唯一键，
     * 抛出的 {@code DuplicateKeyException} 最终被兜底成 500。
     * 于是"删掉角色、想用同一个编码再建一个"这个再正常不过的操作，
     * 会得到一个"系统内部错误"。
     *
     * <p>正解不是绕过唯一索引，而是**复活那一行**：先经
     * {@link RoleMapper#selectAnyByTenantAndCode}（唯一一处绕过逻辑删除的读）
     * 看见软删行，再 {@link RoleMapper#restoreDeletedById} 把它恢复，
     * 最后按本次请求重写全部字段。语义上也更对：{@code create_time} 保留了这个编码
     * 第一次出现的时间，而"删过又建回来"在审计里本就是两条独立记录。
     *
     * <p><b>复活出来的一定是一个空角色。</b>除了角色行本身，还会把该角色名下
     * 残留的有效授权**与成员关系**一并逻辑删除。"复活 = 没有权限、也没有人持有"
     * 因此成为**无条件**成立的语义 —— 否则如果某行角色是被直接改库删掉的
     * （关系行还在），复活会**静默恢复**一批权限，或者让一批人忽然重新拿到这个角色，
     * 而调用方以为自己只是新建了一个同名的空角色。
     *
     * <p>成员关系这一条是后补的：{@link #delete} 在有成员时会拒绝删除（{@code 40916}），
     * 所以正常路径下软删的角色本不该还有有效成员行。但"复活"要防的正是**非正常路径** ——
     * 这一层保护的存在意义就是让语义不依赖"别的代码都写对了"。
     */
    @Transactional
    public RoleVO create(RoleSaveRequest request) {
        String roleCode = request.getRoleCode().trim();
        Long tenantId = resolveTenantId(request.getTenantId());
        RoleEntity parent = resolveParent(request.getParentId(), tenantId);
        String dataScope = resolveDataScope(request.getDataScope());
        String status = resolveStatus(request.getStatus());

        RoleEntity existing = roleMapper.selectAnyByTenantAndCode(tenantId, roleCode);
        boolean occupied = existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0);
        if (occupied) {
            throw AuthResultCode.ROLE_CODE_EXISTS.businessException();
        }

        RoleEntity entity;
        if (existing != null) {
            roleMapper.restoreDeletedById(existing.getId());
            entity = existing;
            // 内存里的 deleted 也要清掉：updateById 把非空字段写进 SET，
            // 留着 1 会把刚恢复的那行当场再删一次（影响 0 行、不报错）
            entity.setDeleted(0);
            clearGrantsOf(entity.getId());
            clearMembersOf(entity.getId());
        } else {
            entity = new RoleEntity();
            entity.setRoleCode(roleCode);
            entity.setTenantId(tenantId);
        }

        applyTo(entity, request, parent, dataScope, status);
        if (existing == null) {
            roleMapper.insert(entity);
        } else {
            updateColumns(entity);
        }
        return toVO(entity);
    }

    /**
     * 修改角色。
     *
     * <p>{@code roleCode} 与 {@code tenantId} 不在修改项内：前者是对外稳定标识，
     * 后者决定这个角色属于谁 —— 把角色"搬到"另一个租户会让它瞬间与所有成员关系错位。
     * 两者都是"要变就新建"，不是一次普通字段更新。
     */
    @Transactional
    public RoleVO update(Long id, RoleSaveRequest request) {
        RoleEntity entity = requireRole(id);
        RoleEntity parent = resolveParent(request.getParentId(), entity.getTenantId());
        assertNotSelfOrDescendant(entity, parent);
        String dataScope = resolveDataScope(request.getDataScope());
        String status = resolveStatus(request.getStatus());

        if (isSystemRole(entity)) {
            // 两道保护，缺一不可：
            //  1) 不能停用 —— 停用后系统里再没有人能管理权限，恢复只能改库；
            //  2) 不能把分类改走 —— 否则"先降级为普通角色、再删除"就是一条绕过路径。
            if (!Status.ENABLED.wireName().equals(status)) {
                throw AuthResultCode.SYSTEM_ROLE_PROTECTED.businessException("系统内置角色不可停用");
            }
            String category = normalize(request.getCategory());
            if (category != null && !CATEGORY_SYSTEM.equalsIgnoreCase(category)) {
                throw AuthResultCode.SYSTEM_ROLE_PROTECTED.businessException(
                        "系统内置角色的分类不可修改，否则可经由「先降级为普通角色、再删除」绕过保护");
            }
        }

        applyTo(entity, request, parent, dataScope, status);
        updateColumns(entity);
        return toVO(entity);
    }

    /**
     * 删除角色（逻辑删除），三道拒绝。
     *
     * <ol>
     *   <li><b>系统内置角色不删。</b>{@code category = SYSTEM} 的角色是管理系统的起点，
     *       删掉它等于所有人同时失去管理能力，而恢复手段只剩直接改库 ——
     *       一次点击造成需要连数据库才能撤销的后果，不该由 HTTP 接口提供。</li>
     *   <li><b>有子角色不删。</b>与租户 / 资源一致：不提供藏起来的级联。
     *       删掉父角色会让子角色变成孤儿（层级信息已经丢了，且没有接口能修回来）。</li>
     *   <li><b>有成员不删。</b>这是本类最要紧的一条。角色是逻辑删除，
     *       删掉之后判定 SQL 的 {@code ro.deleted = 0} 会让它立刻退出判定 ——
     *       也就是说，一次 delete 会让**持有它的那些人**静默地少掉一批权限。
     *       要求先解除成员关系（那时能看到受影响的是哪些人），再删角色。</li>
     * </ol>
     *
     * <p><b>角色自己的授权不构成拒绝理由，会被一并逻辑删除。</b>这与
     * "删资源时若有角色引用则拒绝"是**两条不同的规则**，容易被看成不一致，所以写清楚：
     * 授权关系是角色的**内部边**，角色没了它就没有意义（不是"别人的数据被改动"）；
     * 而"删资源"破坏的是别的聚合（角色的授权集合），所以才要被拒绝。
     * 一并删除还有一层安全考虑：若留存，复活角色会让这批权限**静默回来**。
     */
    @Transactional
    public void delete(Long id) {
        RoleEntity entity = requireRole(id);

        if (isSystemRole(entity)) {
            throw AuthResultCode.SYSTEM_ROLE_PROTECTED.businessException("系统内置角色不可删除");
        }

        Long childCount = roleMapper.selectCount(Wrappers.<RoleEntity>lambdaQuery()
                .eq(RoleEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                    "该角色下仍有 " + childCount + " 个子角色，请先处理子角色");
        }

        Long memberCount = relRoleMemberMapper.selectCount(Wrappers.<RelRoleMemberEntity>lambdaQuery()
                .eq(RelRoleMemberEntity::getRoleId, id));
        if (memberCount != null && memberCount > 0) {
            throw AuthResultCode.ROLE_IN_USE.businessException(
                    "该角色仍被 " + memberCount + " 个成员持有，请先解除成员关系");
        }

        clearGrantsOf(id);
        roleMapper.deleteById(entity.getId());
    }

    /* ------------------------------------------------------------------ 角色资源授权 */

    @Transactional(readOnly = true)
    public List<RoleResourceVO> listGrants(Long roleId) {
        requireRole(roleId);
        long now = System.currentTimeMillis();
        return roleMapper.selectResourceGrants(roleId).stream()
                .peek(vo -> {
                    vo.setRequestScoped(requestScopedOf(vo.getResourceType()));
                    vo.setEffectiveNow(isWithin(vo.getEffectiveFrom(), vo.getEffectiveTo(), now));
                })
                .toList();
    }

    /**
     * 给角色授予一个资源（即一个权限码）。
     *
     * <p><b>upsert 而不是"先删后插"或"直接插"：</b>
     * <ul>
     *   <li>直接插会在"改效果"（ALLOW → DENY）时留下两条相反的行。判定按资源聚合、
     *       DENY 胜出，于是**旧的 ALLOW 还在生效状态**，而调用方以为改成了 DENY ——
     *       实际效果是"这条授权忽然不生效了"，与预期正好相反。</li>
     *   <li>先删后插会让 {@code create_time} 每次都刷新，授权"从什么时候开始"这段历史
     *       就丢了；而审计面板只记变更后的值，追溯不到。</li>
     * </ul>
     * 因此按 (角色, 资源) 定位到唯一一条有效行为目标：存在则改它的字段，
     * 存在但已被撤销则**复活**它，不存在才新建。**并且把同键的其余行全部撤销** ——
     * 表上没有 (role_id, resource_id) 唯一索引，服务层的"先查后写"挡不住并发，
     * 残留的重复行会让一条历史 DENY 复活，把一次授权变成静默的拒绝。
     *
     * <p><b>不拒绝给"已停用的资源"授权。</b>那种授权当前不产生权限（判定要求
     * {@code is_enabled = 1}），但允许先配置、后启用是合理的运维顺序；
     * 出参里的 {@code isEnabled} 让界面能提示这件事，不必用 400 拦住。
     *
     * <p>时间区间写反（起始晚于结束）会被拒绝：那是一条**永远不会生效**的授权，
     * 而它在列表里看起来完全正常。
     */
    @Transactional
    public RoleResourceVO grant(Long roleId, RoleResourceGrantRequest request) {
        RoleEntity role = requireRole(roleId);
        ResourceEntity resource = requireResource(request.getResourceId());
        String effect = resolveEffect(request.getEffect());
        int priority = request.getPriority() == null ? 0 : request.getPriority();
        Long from = request.getEffectiveFrom();
        Long to = request.getEffectiveTo();
        if (from != null && to != null && from >= to) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "生效区间的起始时间必须早于结束时间，否则这条授权永远不会生效");
        }

        List<RelRoleResourceEntity> existing =
                relRoleResourceMapper.selectAnyByRoleAndResource(roleId, resource.getId());
        RelRoleResourceEntity grant;
        if (existing.isEmpty()) {
            grant = new RelRoleResourceEntity();
            grant.setTenantId(role.getTenantId());
            grant.setRoleId(roleId);
            grant.setResourceId(resource.getId());
            grant.setEffect(effect);
            grant.setPriority(priority);
            grant.setEffectiveFrom(from);
            grant.setEffectiveTo(to);
            relRoleResourceMapper.insert(grant);
            return toResourceVO(grant, resource);
        }

        grant = existing.get(0);
        revokeRedundantRows(existing);
        if (grant.getDeleted() != null && grant.getDeleted() != 0) {
            relRoleResourceMapper.restoreDeletedById(grant.getId());
            grant.setDeleted(0);
        }
        grant.setEffect(effect);
        grant.setPriority(priority);
        grant.setEffectiveFrom(from);
        grant.setEffectiveTo(to);
        // 逐列显式 set，理由见 updateColumns：updateById 的 NOT_NULL 策略清不掉时间窗，
        // 于是"把临时代理改回长期有效"会变成一条永远不生效的授权
        relRoleResourceMapper.update(new RelRoleResourceEntity(),
                Wrappers.<RelRoleResourceEntity>lambdaUpdate()
                        .eq(RelRoleResourceEntity::getId, grant.getId())
                        .set(RelRoleResourceEntity::getEffect, effect)
                        .set(RelRoleResourceEntity::getPriority, priority)
                        .set(RelRoleResourceEntity::getEffectiveFrom, from)
                        .set(RelRoleResourceEntity::getEffectiveTo, to));
        return toResourceVO(grant, resource);
    }

    /**
     * 撤销一条授权（逻辑删除）。
     *
     * <p>撤销前要求"确实持有"：对一个本来就没授过的资源返回成功，会让调用方
     * 以为"已经撤掉了"，而真实情况是从来没授过 —— 两种情况在前端看起来一样，
     * 排查"为什么权限没撤掉"时方向会被带偏。返回 40407 让差异可见。
     *
     * <p>撤销是**逐条**的，没有"清空某角色的全部授权"这种接口：后者一次点击可能
     * 让一批人同时失去权限，而审计里只有一条记录。逐个撤销让每一条都可解释。
     */
    @Transactional
    public void revoke(Long roleId, Long resourceId) {
        requireRole(roleId);
        List<RelRoleResourceEntity> rows =
                relRoleResourceMapper.selectAnyByRoleAndResource(roleId, resourceId);
        List<RelRoleResourceEntity> active = rows.stream()
                .filter(row -> row.getDeleted() == null || row.getDeleted() == 0)
                .toList();
        if (active.isEmpty()) {
            throw AuthResultCode.ROLE_GRANT_NOT_FOUND.businessException();
        }
        active.forEach(row -> relRoleResourceMapper.deleteById(row.getId()));
    }

    /* ------------------------------------------------------------------ 取数与校验 */

    private RoleEntity requireRole(Long id) {
        RoleEntity entity = id == null ? null : roleMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.ROLE_NOT_FOUND.businessException();
        }
        return entity;
    }

    private ResourceEntity requireResource(Long id) {
        ResourceEntity entity = id == null ? null : resourceMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException("引用的资源不存在：" + id);
        }
        return entity;
    }

    /**
     * 租户：显式指定则**校验存在性**，否则取当前登录用户的租户。
     *
     * <p>与 {@code UserService.resolveTenantId}（只取不校验）有意不同，理由是
     * {@code tenant_id} 在角色上的作用更强：角色只能被**本租户**的用户/用户组持有
     * （判定 SQL 两侧都限定 {@code tenant_id}），所以一个写错租户的角色
     * 是**永远不可能被授予给任何人**的。它会静默地躺在库里、任何租户的列表里
     * 都查不到它属于谁 —— 静默不可达的数据，正是本仓尽量不让接口制造的东西。
     *
     * <p>同类引用（父角色、资源）也一律校验，规则统一为"引用了不存在的外键 → 40002"。
     */
    private Long resolveTenantId(Long requested) {
        if (requested != null) {
            assertTenantExists(requested);
            return requested;
        }
        Long current = AuthContextHolder.currentTenantId();
        if (current == null) {
            throw AuthResultCode.PARAM_INVALID.businessException("无法确定租户，请显式指定 tenantId");
        }
        return current;
    }

    private void assertTenantExists(Long tenantId) {
        if (tenantMapper.selectById(tenantId) == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException("引用的租户不存在：" + tenantId);
        }
    }

    private RoleEntity resolveParent(Long parentId, Long tenantId) {
        if (parentId == null) {
            return null;
        }
        RoleEntity parent = roleMapper.selectById(parentId);
        if (parent == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException("引用的父角色不存在：" + parentId);
        }
        if (!parent.getTenantId().equals(tenantId)) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "父角色不属于同一租户，跨租户的层级会造成判定上的错位");
        }
        return parent;
    }

    /**
     * 拒绝把角色挂到自己或自己的后代下面。
     *
     * <p>与 {@code ResourceService} 同一条思路，但走的是 {@code parent_id} 主键链、
     * 且必须带 {@code seen} 终止保证：库里若已存在环（表可被直接改），
     * 没有它这里会转不出来。
     */
    private void assertNotSelfOrDescendant(RoleEntity self, RoleEntity parent) {
        if (parent == null) {
            return;
        }
        if (parent.getId().equals(self.getId())) {
            throw AuthResultCode.HIERARCHY_CYCLE.businessException("不能把角色挂到自己下面");
        }
        Set<Long> seen = new HashSet<>();
        RoleEntity cursor = parent;
        while (cursor != null) {
            if (cursor.getId().equals(self.getId())) {
                throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                        "不能把角色挂到自己的下级下面：" + parent.getId());
            }
            if (!seen.add(cursor.getId())) {
                return;
            }
            cursor = cursor.getParentId() == null ? null : roleMapper.selectById(cursor.getParentId());
        }
    }

    /* ------------------------------------------------------------------ 字段组装 */

    private static void applyTo(RoleEntity entity, RoleSaveRequest request,
                                RoleEntity parent, String dataScope, String status) {
        entity.setRoleName(request.getRoleName().trim());
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setRoleLevel(resolveLevel(request.getRoleLevel(), parent));
        entity.setCategory(normalize(request.getCategory()));
        entity.setDataScope(dataScope);
        entity.setFilterJson(normalize(request.getFilterJson()));
        entity.setInheritStrategy(normalize(request.getInheritStrategy()));
        entity.setIsDefault(flag(request.getIsDefault(), 0, "isDefault"));
        entity.setSortNo(orDefault(request.getSortNo(), 0));
        entity.setStatus(status);
        entity.setDescription(normalize(request.getDescription()));
    }

    /** 层级没传就按父角色 +1 推出来，而不是留空 —— 留空的层级列排序时会跑到最前 */
    private static Integer resolveLevel(Integer requested, RoleEntity parent) {
        if (requested != null) {
            if (requested < 1) {
                throw AuthResultCode.PARAM_INVALID.businessException(
                        "角色层级须不小于 1，收到：" + requested);
            }
            return requested;
        }
        int parentLevel = parent == null || parent.getRoleLevel() == null
                ? DEFAULT_ROLE_LEVEL - 1 : parent.getRoleLevel();
        return parentLevel + 1;
    }

    private static RoleVO toVO(RoleEntity entity) {
        RoleVO vo = new RoleVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setRoleCode(entity.getRoleCode());
        vo.setRoleName(entity.getRoleName());
        vo.setParentId(entity.getParentId());
        vo.setRoleLevel(entity.getRoleLevel());
        vo.setCategory(entity.getCategory());
        vo.setIsDefault(entity.getIsDefault());
        vo.setInheritStrategy(entity.getInheritStrategy());
        vo.setDataScope(entity.getDataScope());
        vo.setFilterJson(entity.getFilterJson());
        vo.setSortNo(entity.getSortNo());
        vo.setStatus(entity.getStatus());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static RoleResourceVO toResourceVO(RelRoleResourceEntity grant, ResourceEntity resource) {
        RoleResourceVO vo = new RoleResourceVO();
        vo.setGrantId(grant.getId());
        vo.setResourceId(resource.getId());
        vo.setResourceCode(resource.getResourceCode());
        vo.setResourceName(resource.getResourceName());
        vo.setResourceType(resource.getResourceType());
        vo.setRequestScoped(requestScopedOf(resource.getResourceType()));
        vo.setIsEnabled(resource.getIsEnabled());
        vo.setEffect(grant.getEffect());
        vo.setPriority(grant.getPriority());
        vo.setEffectiveFrom(grant.getEffectiveFrom());
        vo.setEffectiveTo(grant.getEffectiveTo());
        vo.setEffectiveNow(isWithin(grant.getEffectiveFrom(), grant.getEffectiveTo(),
                System.currentTimeMillis()));
        vo.setCreateTime(grant.getCreateTime());
        return vo;
    }

    /**
     * 撤销该角色名下全部有效授权。
     *
     * <p>用 MyBatis-Plus 的 {@code delete(wrapper)} 而不是手写 SQL：它认
     * {@code @TableLogic}，会翻成 {@code UPDATE ... SET deleted = 1 WHERE deleted = 0} ——
     * 正是这里要的语义（就地软删、不物理删除，审计与 {@code create_time} 都留着）。
     */
    private void clearGrantsOf(Long roleId) {
        relRoleResourceMapper.delete(Wrappers.<RelRoleResourceEntity>lambdaQuery()
                .eq(RelRoleResourceEntity::getRoleId, roleId));
    }

    /**
     * 撤销该角色名下全部成员关系。
     *
     * <p>目前只有一个调用点：{@link #create} 的复活分支。它的存在是为了让
     * "复活 = 一个没人持有、也没有权限的角色"无条件成立 —— 见那里的注释。
     *
     * <p>与 {@link #clearGrantsOf} 同形：{@code delete(wrapper)} 认 {@code @TableLogic}，
     * 翻成 {@code UPDATE ... SET deleted = 1 WHERE deleted = 0}，就地软删、不物理删除。
     * 之所以要"就地"，是因为"这个人曾经被授过这个角色、后来被解除"本身就是审计要回答的问题，
     * 物理删掉它就等于把历史抹了。
     */
    private void clearMembersOf(Long roleId) {
        relRoleMemberMapper.delete(Wrappers.<RelRoleMemberEntity>lambdaQuery()
                .eq(RelRoleMemberEntity::getRoleId, roleId));
    }

    /**
     * 更新可变列 —— **逐列显式 {@code set}，不用 {@code updateById}**。
     *
     * <p>这条不是风格偏好，而是一个会静默改变行为的差别。MyBatis-Plus 默认的字段策略是
     * {@code NOT_NULL}：{@code updateById(entity)} 只把**非空**字段拼进 {@code SET}，
     * 因此"把某个字段改回 null"这个操作**根本发不出去** —— 语句影响 0 行、不报错、
     * 返回值也无异常，调用方拿到 200，库里那一列却还是旧值。
     *
     * <p>本类上有三处会因此变成假功能，都是真实会遇到的：
     * <ul>
     *   <li>{@code parentId} 改回 null（把角色从层级里摘出来放回顶级）—— 传了 null，
     *       角色却还挂在原来的父角色下面，且没有任何提示；</li>
     *   <li>{@code description} / {@code filterJson} / {@code category} 清空 ——
     *       用户删掉描述点保存，刷新后描述又回来了；</li>
     *   <li>授权行上的 {@code effectiveFrom / effectiveTo}（见 {@link #grant}）——
     *       把"临时代理"改回长期有效是**清掉时间窗**，而清不掉就意味着这条授权
     *       永远停在过期状态，表现为"我明明重新授了权，怎么还是没权限"。</li>
     * </ul>
     * 第三处是端到端测试逮出来的：断言"恢复长期有效后权限应当回来"时拿到了 40301，
     * 一路查到 SQL 才发现时间窗从未被清掉。
     *
     * <p>{@code update(wrapper)} 传一个空的 {@code RoleEntity} 而不是 {@code null}：
     * 空实体本身不贡献任何 {@code SET}（字段全为 null，策略把它们排除），
     * 但它让 MyBatis-Plus 的 {@code updateFill} 有机会跑起来，
     * 否则 {@code update_time / update_by} 会在这条 UPDATE 上停更 ——
     * 而"这条授权/角色上次改于何时"正是审计要回答的问题。
     */
    private void updateColumns(RoleEntity entity) {
        roleMapper.update(new RoleEntity(), Wrappers.<RoleEntity>lambdaUpdate()
                .eq(RoleEntity::getId, entity.getId())
                .set(RoleEntity::getRoleName, entity.getRoleName())
                .set(RoleEntity::getParentId, entity.getParentId())
                .set(RoleEntity::getRoleLevel, entity.getRoleLevel())
                .set(RoleEntity::getCategory, entity.getCategory())
                .set(RoleEntity::getDataScope, entity.getDataScope())
                .set(RoleEntity::getFilterJson, entity.getFilterJson())
                .set(RoleEntity::getInheritStrategy, entity.getInheritStrategy())
                .set(RoleEntity::getIsDefault, entity.getIsDefault())
                .set(RoleEntity::getSortNo, entity.getSortNo())
                .set(RoleEntity::getStatus, entity.getStatus())
                .set(RoleEntity::getDescription, entity.getDescription()));
    }

    /** 把同键的其余行撤销掉，只留下 {@code target} 这一条 —— 见 {@link #grant} */
    private void revokeRedundantRows(List<RelRoleResourceEntity> rows) {
        for (int i = 1; i < rows.size(); i += 1) {
            RelRoleResourceEntity extra = rows.get(i);
            if (extra.getDeleted() == null || extra.getDeleted() == 0) {
                relRoleResourceMapper.deleteById(extra.getId());
            }
        }
    }

    /* ------------------------------------------------------------------ 枚举与归一 */

    private static boolean isSystemRole(RoleEntity entity) {
        return CATEGORY_SYSTEM.equalsIgnoreCase(entity.getCategory());
    }

    /** 写入时严格：档位不合法就报错，并把合法取值列进文案 */
    private static String resolveDataScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataScope.ALL.wireName();
        }
        DataScope parsed = DataScope.parse(raw);
        if (parsed == null) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "数据范围不合法，取值：" + Arrays.stream(DataScope.values())
                            .map(DataScope::wireName)
                            .collect(Collectors.joining(" / ")));
        }
        return parsed.wireName();
    }

    private static String resolveStatus(String status) {
        Status parsed = Status.parse(status);
        return parsed == null ? Status.ENABLED.wireName() : parsed.wireName();
    }

    /**
     * 过滤时宽松：认得就归一成标准写法（{@code enabled} → {@code ENABLED}），
     * 认不得就原样拿去比 —— 过滤条件不该让查询失败，结果为空比弹错更接近预期。
     */
    private static String normalizeStatusFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Status parsed = Status.parse(raw);
        return parsed == null ? raw.trim() : parsed.wireName();
    }

    private static String resolveEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return GrantEffect.ALLOW.wireName();
        }
        GrantEffect parsed = GrantEffect.parse(raw);
        if (parsed == null) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "授权效果不合法，取值：" + Arrays.stream(GrantEffect.values())
                            .map(GrantEffect::wireName)
                            .collect(Collectors.joining(" / ")));
        }
        return parsed.wireName();
    }

    private static Boolean requestScopedOf(String resourceType) {
        ResourceType parsed = ResourceType.parse(resourceType);
        return parsed == null ? null : parsed.isRequestScoped();
    }

    /** 半开区间：[from, to)。两端都可空，都空表示长期有效 */
    private static Boolean isWithin(Long from, Long to, long now) {
        if (from != null && now < from) {
            return false;
        }
        return to == null || now < to;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer orDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 三态标志位只接受 0 / 1 —— 理由同 {@code ResourceService.flag}：
     * 放行别的值不会报错，只会静默改变语义。
     */
    private static Integer flag(Integer value, int defaultValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }
        if (value != 0 && value != 1) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    fieldName + " 只能是 0 或 1，收到：" + value);
        }
        return value;
    }

}
