package com.bluewhale.sts.auth.usergroup;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.MemberType;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.relation.RelRoleMemberEntity;
import com.bluewhale.sts.auth.relation.RelRoleMemberMapper;
import com.bluewhale.sts.auth.relation.RelUserGroupMemberEntity;
import com.bluewhale.sts.auth.relation.RelUserGroupMemberMapper;
import com.bluewhale.sts.auth.tenant.TenantService;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import com.bluewhale.sts.common.db.query.KeywordLike;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户组应用服务 —— 用户组的增删改查，以及**把用户加进组 / 移出组**。
 *
 * <p>它补上的是 RBAC 判定链配置侧的**最后一条边**。在此之前链路已经能走通
 * （资源 → 角色 → 成员），但"经用户组"那一段只能由种子或直接写库产生：
 * {@code RoleMemberService} 的成员类型接受 {@code GROUP}，而
 * {@code bw_auth_user_group} 与它的成员关系表**没有任何接口**。
 * 端到端测试里那段"用户 → 用户组 → 角色 → 资源"的夹具因此是硬写进去的。
 *
 * <p><b>本类同时修掉一个判定侧的静默缺陷：停用的用户组一直在产生权限。</b>
 * 表的 {@code is_enabled} 列从建库起就在，实体也一直带着 {@code isEnabled} 字段，
 * 但 {@code AuthorizationMapper} 的子查询只读 {@code bw_auth_rel_user_group_member}，
 * **从不连 {@code bw_auth_user_group}** —— 也就是说这一列此前是纯粹摆设：
 * 把组停用，组里的每个人权限照旧。本切片把它接进判定（连表检查 {@code deleted = 0}
 * 与 {@code is_enabled}），与 ADR-0022 修掉的"停用角色不生效"是同一类缺陷的第五处。
 * 不修的话，本类交付的 {@code isEnabled} 就是第六个假开关。
 *
 * <p><b>三条比角色更严或不同的约束：</b>
 * <ul>
 *   <li><b>不接收 {@code GROUP} 成员</b>（返回 40001）。组嵌套在判定链路里
 *       **不生效** —— 判定只跳一层。接受它会让成员列表里出现一条"看起来生效、
 *       实际不参与判定"的关系，正是本仓最不接受的东西。详见 {@code MemberType}；</li>
 *   <li><b>只支持 {@code STATIC}</b>。动态成员求值未实现，接受 {@code DYNAMIC}
 *       会得到一个永远算不出成员的组；</li>
 *   <li><b>删除时若仍有成员则拒绝</b>（{@code 40918}），理由与角色侧的
 *       {@code ROLE_IN_USE} 完全对称：删组会让组内**每一个人**同时少掉一批权限，
 *       而删的本意可能只是"这个部门撤了"。</li>
 * </ul>
 *
 * <p><b>{@code parentId} 被接受但不产生权限。</b>组与组的层级不参与判定
 * （见 {@code UserGroupEntity} 的注释），保留这一列是为了列表分组展示，
 * 与角色的 {@code parentId} 是同一种处理。因此这里也只做两件它必须保证的事：
 * 父组必须存在、必须同租户，以及不许成环 —— 一列"只用于展示"的信息若自己成了环，
 * 展示逻辑就会转不出来。
 */
@Service
public class UserGroupService {

    /** 当前唯一支持的分组方式。另见 {@link UserGroupSaveRequest#getCategory()} */
    private static final String CATEGORY_STATIC = "STATIC";

    private final UserGroupMapper userGroupMapper;
    private final RelUserGroupMemberMapper relUserGroupMemberMapper;
    private final RelRoleMemberMapper relRoleMemberMapper;
    private final UserMapper userMapper;
    private final TenantService tenantService;

    public UserGroupService(UserGroupMapper userGroupMapper,
                            RelUserGroupMemberMapper relUserGroupMemberMapper,
                            RelRoleMemberMapper relRoleMemberMapper,
                            UserMapper userMapper,
                            TenantService tenantService) {
        this.userGroupMapper = userGroupMapper;
        this.relUserGroupMemberMapper = relUserGroupMemberMapper;
        this.relRoleMemberMapper = relRoleMemberMapper;
        this.userMapper = userMapper;
        this.tenantService = tenantService;
    }

    /* ------------------------------------------------------------------ 用户组 CRUD */

    @Transactional(readOnly = true)
    public PageResult<UserGroupVO> page(UserGroupQuery query) {
        // 关键词先规范化（吃掉两端空白）再转义，见 KeywordLike（decisions/0067 · 0068）。
        String pattern = KeywordLike.pattern(query.getKeyword());
        // 过滤值先取局部变量：`eq(condition, 列, 值)` 的"值"是**无条件求值**的，
        // 写成 `query.getCategory().trim()` 会在 category 为空时当场 NPE，
        // 外层的条件参数救不了。这条坑在本仓已经踩过两次（资源目录、角色）。
        // category 是自由文本（只校验形状、不校验取值集）⇒ 只 trim。
        String category = FilterValue.text(query.getCategory());
        Long tenantId = resolveTenantId(query.getTenantId());

        var wrapper = Wrappers.<UserGroupEntity>lambdaQuery()
                .eq(UserGroupEntity::getTenantId, tenantId)
                .and(pattern != null, w -> w
                        .apply(KeywordLike.condition("group_code"), pattern)
                        .or().apply(KeywordLike.condition("group_name"), pattern))
                .eq(category != null, UserGroupEntity::getCategory, category)
                .eq(query.getEnabled() != null, UserGroupEntity::getIsEnabled, query.getEnabled())
                .eq(query.getParentId() != null, UserGroupEntity::getParentId, query.getParentId())
                // 先 sortNo 再 id：只按 sortNo 排时，同序记录在不同页之间的相对顺序
                // 由数据库决定，"翻页时某条记录出现两次或一次都不出现"就是这么来的
                .orderByAsc(UserGroupEntity::getSortNo)
                .orderByAsc(UserGroupEntity::getId);

        Page<UserGroupEntity> page =
                userGroupMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<UserGroupVO> records = page.getRecords().stream().map(UserGroupService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public UserGroupVO detail(Long id) {
        return toVO(requireGroup(id));
    }

    /**
     * 新建用户组。
     *
     * <p><b>核心与 {@code RoleService.create} 是同一支：编码被一条已删除的组占着。</b>
     * 唯一索引 {@code uk_bw_auth_user_group_tenant_code} 建在
     * {@code (tenant_id, group_code)} 上、**不含 {@code deleted}**，
     * 而 MyBatis-Plus 给所有自动查询追加 {@code deleted = 0} —— 常规判重看不见软删行，
     * 判重通过后 insert 撞唯一键，最终落到兜底的 {@code 40900}
     * 「数据已存在或与已有记录冲突」（{@code ADR-0024} 之前是 500）。
     *
     * <p>正解不是绕过唯一索引，而是**复活那一行**，并且在复活时清掉两样东西：
     * 残留的成员关系与这个组持有的角色。这样"复活 = 一个没有成员、也没有任何角色
     * 的空组"就成为**无条件**成立的语义 —— 反过来，若留着它们，
     * 复活会让一批人**静默地重新拿到**这个组带来的权限，
     * 而调用方以为自己只是新建了一个同名的空组。
     *
     * <p>{@link #delete} 那道"有成员则拒绝"的守卫只管**正常路径**；
     * 复活分支要防的正是非正常路径（组被直接改库删掉、成员行还在）。
     * 两层保护的存在意义就是让语义不依赖"别的代码都写对了"。
     */
    @Transactional
    public UserGroupVO create(UserGroupSaveRequest request) {
        String groupCode = request.getGroupCode().trim();
        Long tenantId = resolveTenantId(request.getTenantId());
        // 同 {@link RoleService#create}：新建才问这一格 —— 停用租户里的组
        // 只会被本租户的角色成员关系引用，而那个租户的用户登不进来。见 ADR-0051
        tenantService.assertAcceptsNewData(tenantId);
        UserGroupEntity parent = resolveParent(request.getParentId(), tenantId);
        String category = resolveCategory(request.getCategory());

        UserGroupEntity existing = userGroupMapper.selectAnyByTenantAndCode(tenantId, groupCode);
        boolean occupied = existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0);
        if (occupied) {
            throw AuthResultCode.USER_GROUP_CODE_EXISTS.businessException();
        }

        UserGroupEntity entity;
        if (existing != null) {
            userGroupMapper.restoreDeletedById(existing.getId());
            entity = existing;
            // 内存里的 deleted 也要清掉：update 把非空字段写进 SET，留着 1 会再把刚恢复的行删一次
            entity.setDeleted(0);
            clearMembersOf(entity.getId());
            clearRoleGrantsOf(entity.getId());
        } else {
            entity = new UserGroupEntity();
            entity.setGroupCode(groupCode);
            entity.setTenantId(tenantId);
        }

        applyTo(entity, request, parent, category);
        if (existing == null) {
            userGroupMapper.insert(entity);
        } else {
            updateColumns(entity);
        }
        return toVO(entity);
    }

    /**
     * 修改用户组。
     *
     * <p>{@code groupCode} 与 {@code tenantId} 不在修改项内：前者是对外稳定标识，
     * 后者决定这个组属于谁 —— 把组"搬到"另一个租户会让它瞬间与所有成员、
     * 所有角色授权错位。两者都是"要变就新建"。
     *
     * <p>{@code isEnabled} 可以改，而且它是**真开关**：停用的组立刻不再产生权限
     * （判定侧连表检查，见类注释）。这正是本类要交付的东西 ——
     * 在此之前改它是没有任何效果的。
     */
    @Transactional
    public UserGroupVO update(Long id, UserGroupSaveRequest request) {
        UserGroupEntity entity = requireGroup(id);
        UserGroupEntity parent = resolveParent(request.getParentId(), entity.getTenantId());
        assertNotSelfOrDescendant(entity, parent);
        String category = resolveCategory(request.getCategory());

        applyTo(entity, request, parent, category);
        updateColumns(entity);
        return toVO(entity);
    }

    /**
     * 删除用户组（逻辑删除）。
     *
     * <p>两道拒绝：有成员（{@code 40918}）、有子组（{@code 40913}）。
     * 前者是最要紧的一条 —— 用户组是逻辑删除，删掉之后判定 SQL 里
     * {@code gm.user_group_id IN (...)} 立刻取不到它，"这个组从某个角色那里
     * 得到的权限"对组里每一个人同时消失，而没有任何界面会提示这件事发生过。
     *
     * <p><b>组自己持有的角色不构成拒绝理由，会被一并撤销</b> —— 与
     * {@code RoleService.delete} 里"角色自身的授权一并删除"完全同构：
     * 成员既然已经清空，那些角色授权就不再影响任何人，而它们属于**这个组自己的配置**。
     */
    @Transactional
    public void delete(Long id) {
        UserGroupEntity entity = requireGroup(id);

        Long memberCount = relUserGroupMemberMapper.selectCount(
                Wrappers.<RelUserGroupMemberEntity>lambdaQuery()
                        .eq(RelUserGroupMemberEntity::getUserGroupId, id));
        if (memberCount != null && memberCount > 0) {
            throw AuthResultCode.USER_GROUP_IN_USE.businessException(
                    "该用户组仍包含 " + memberCount + " 个成员，请先移除成员");
        }

        Long childCount = userGroupMapper.selectCount(Wrappers.<UserGroupEntity>lambdaQuery()
                .eq(UserGroupEntity::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                    "该用户组下仍有 " + childCount + " 个子组，请先处理子组");
        }

        clearRoleGrantsOf(id);
        userGroupMapper.deleteById(entity.getId());
    }

    /* ------------------------------------------------------------------ 组成员关系 */

    /**
     * 分页查询组成员，可按成员类型过滤。
     *
     * <p>过滤值先取局部变量再进 {@code eq}，理由见 {@link #page} 的注释。
     */
    @Transactional(readOnly = true)
    public PageResult<UserGroupMemberVO> pageMembers(Long groupId, UserGroupMemberQuery query) {
        requireGroup(groupId);
        String memberType = FilterValue.enumValue(query.getMemberType(),
                MemberType::parse, MemberType::wireName);

        Page<RelUserGroupMemberEntity> page = relUserGroupMemberMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<RelUserGroupMemberEntity>lambdaQuery()
                        .eq(RelUserGroupMemberEntity::getUserGroupId, groupId)
                        .eq(memberType != null, RelUserGroupMemberEntity::getMemberType, memberType)
                        // 只按主键排：加入顺序就是排查时想看的顺序，且主键单调、翻页稳定
                        .orderByAsc(RelUserGroupMemberEntity::getId));

        return PageResult.of(toMemberVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getSize());
    }

    /**
     * 把一个用户加进用户组（upsert）。
     *
     * <p>upsert 而不是"直接插"或"先删后插"，与 {@code RoleMemberService.bind} 同一套理由：
     * 直接插会在"把临时组员改回长期有效"时留下两条同键的行；先删后插会让
     * {@code create_time} 每次刷新，这段关系"从什么时候开始"就追溯不到了。
     * 因此按 (组, 成员类型, 成员主键) 定位唯一一条有效行为目标：存在则改它的时间窗，
     * 存在但已被移除则**复活**它，不存在才新建。**并把同键的其余行全部逻辑删除** ——
     * 表上没有这个三元组的唯一索引，服务层的"先查后写"挡不住并发，
     * 残留的重复行会让一段历史成员关系复活。
     *
     * <p>时间窗用**逐列显式 {@code set}**，不用 {@code updateById}：
     * MyBatis-Plus 默认的 {@code NOT_NULL} 字段策略不会把 {@code null} 拼进 {@code SET}，
     * 于是"把临时组员改成长期有效"（= 清掉时间窗）会**影响 0 行且不报错**。
     * 授权侧已经因此付出过一次代价，见 {@code RoleService.updateColumns}。
     *
     * <p><b>不拒绝把用户加进"已停用的组"。</b>它当前不产生权限，
     * 但"先配置、后启用"是合理的运维顺序（新部门的人员先挂好、上线当天再启用）。
     * 与 {@code RoleMemberService} 不拒绝给已停用角色加成员是同一条判断。
     */
    @Transactional
    public UserGroupMemberVO bindMember(Long groupId, UserGroupMemberBindRequest request) {
        UserGroupEntity group = requireGroup(groupId);
        MemberType memberType = resolveMemberType(request.getMemberType());
        if (memberType != MemberType.USER) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "用户组当前只接受 USER 成员：组的嵌套在判定链路里不生效"
                            + "（判定只跳一层），接受它会产生一条看起来生效、实际不参与判定的关系");
        }

        UserEntity user = request.getMemberId() == null ? null
                : userMapper.selectById(request.getMemberId());
        if (user == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "引用的用户不存在：" + request.getMemberId());
        }
        assertSameTenant(group.getTenantId(), user.getTenantId(), user.getId());

        Long from = request.getEffectiveFrom();
        Long to = request.getEffectiveTo();
        if (from != null && to != null && from >= to) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "生效区间的起始时间必须早于结束时间，否则这段组成员关系永远不会生效");
        }

        List<RelUserGroupMemberEntity> existing = relUserGroupMemberMapper
                .selectAnyByGroupAndMember(groupId, memberType.wireName(), user.getId());
        if (existing.isEmpty()) {
            RelUserGroupMemberEntity created = new RelUserGroupMemberEntity();
            // tenantId 取组的，不是用户的 —— 两者已校验相等，用组的那个表达的是
            // "这段关系属于组所在的租户"，与判定 SQL 的 gm.tenant_id 限定对齐
            created.setTenantId(group.getTenantId());
            created.setUserGroupId(groupId);
            created.setMemberId(user.getId());
            created.setMemberType(memberType.wireName());
            created.setEffectiveFrom(from);
            created.setEffectiveTo(to);
            relUserGroupMemberMapper.insert(created);
            return toMemberVO(created, memberType, displayNameOf(user));
        }

        RelUserGroupMemberEntity target = existing.get(0);
        revokeRedundantRows(existing);
        if (target.getDeleted() != null && target.getDeleted() != 0) {
            relUserGroupMemberMapper.restoreDeletedById(target.getId());
            // 内存里的 deleted 也要清掉：update 的 WHERE 带 deleted = 0，留着 1 会静默影响 0 行
            target.setDeleted(0);
        }
        relUserGroupMemberMapper.update(new RelUserGroupMemberEntity(),
                Wrappers.<RelUserGroupMemberEntity>lambdaUpdate()
                        .eq(RelUserGroupMemberEntity::getId, target.getId())
                        .set(RelUserGroupMemberEntity::getEffectiveFrom, from)
                        .set(RelUserGroupMemberEntity::getEffectiveTo, to));
        target.setEffectiveFrom(from);
        target.setEffectiveTo(to);
        return toMemberVO(target, memberType, displayNameOf(user));
    }

    /**
     * 把一个用户移出用户组（逻辑删除）。
     *
     * <p>移除前要求"确实在组里"：对一个本来就不在组里的人返回成功，会让调用方
     * 以为"已经移掉了"，而真相是从来没加过 —— 两种情况在前端看起来一样，
     * 排查"为什么这个人还能进"时方向会被带偏。返回 {@code 40409} 让差异可见。
     *
     * <p>路径里的 {@code memberType} 与角色侧一样不能省：{@code member_id} 是多态列，
     * 用户与用户组是两个独立的自增序列、会撞号。当前组成员恒为 {@code USER}，
     * 但表里可能有历史上直接写库产生的 {@code GROUP} 行，撤销路径按类型定位才能清理它们。
     *
     * <p><b>逐段移除，没有"清空某个组的全部成员"这种接口。</b>那一次点击可能让
     * 几百人同时失去权限，而审计里只有一条记录 —— 正是本仓尽力避免的
     * "改了配置却没人知道改了哪些"。
     */
    @Transactional
    public void revokeMember(Long groupId, String rawMemberType, Long memberId) {
        requireGroup(groupId);
        MemberType memberType = resolveMemberType(rawMemberType);

        List<RelUserGroupMemberEntity> rows = relUserGroupMemberMapper
                .selectAnyByGroupAndMember(groupId, memberType.wireName(), memberId);
        List<RelUserGroupMemberEntity> active = rows.stream()
                .filter(row -> row.getDeleted() == null || row.getDeleted() == 0)
                .toList();
        if (active.isEmpty()) {
            throw AuthResultCode.USER_GROUP_MEMBER_NOT_FOUND.businessException();
        }
        active.forEach(row -> relUserGroupMemberMapper.deleteById(row.getId()));
    }

    /* ------------------------------------------------------------------ 取数与校验 */

    private UserGroupEntity requireGroup(Long id) {
        UserGroupEntity entity = id == null ? null : userGroupMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.USER_GROUP_NOT_FOUND.businessException();
        }
        return entity;
    }

    /**
     * 租户：显式指定则**校验存在性**，否则取当前登录用户的租户。
     *
     * <p>用户组只能被**本租户**的角色成员关系引用，
     * 一个写错租户的组会**永远不可能产生权限** —— 成员加得进去、列表一切正常、
     * 判定永远匹配不上。静默不可达的数据，正是本仓尽量不让接口制造的东西。
     * 校验住在 {@link TenantService#assertExists}（此前四个域各写一遍，见 ADR-0050）。
     */
    private Long resolveTenantId(Long requested) {
        if (requested != null) {
            tenantService.assertExists(requested);
            return requested;
        }
        Long current = AuthContextHolder.currentTenantId();
        if (current == null) {
            throw AuthResultCode.PARAM_INVALID.businessException("无法确定租户，请显式指定 tenantId");
        }
        return current;
    }

    private UserGroupEntity resolveParent(Long parentId, Long tenantId) {
        if (parentId == null) {
            return null;
        }
        UserGroupEntity parent = userGroupMapper.selectById(parentId);
        if (parent == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "引用的父用户组不存在：" + parentId);
        }
        if (!parent.getTenantId().equals(tenantId)) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "父用户组不属于同一租户，跨租户的层级会造成展示上的错位");
        }
        return parent;
    }

    /**
     * 拒绝把用户组挂到自己或自己的后代下面。
     *
     * <p>{@code parentId} 一列虽不参与判定，但成环之后任何按层级展示的逻辑都会转不出来，
     * 所以这条守卫与 {@code RoleService.assertNotSelfOrDescendant} 一样必须存在。
     * 带 {@code seen} 终止保证：库里若已存在环（表可被直接改），没有它会死循环。
     */
    private void assertNotSelfOrDescendant(UserGroupEntity self, UserGroupEntity parent) {
        if (parent == null) {
            return;
        }
        if (parent.getId().equals(self.getId())) {
            throw AuthResultCode.HIERARCHY_CYCLE.businessException("不能把用户组挂到自己下面");
        }
        Set<Long> seen = new HashSet<>();
        UserGroupEntity cursor = parent;
        while (cursor != null) {
            if (cursor.getId().equals(self.getId())) {
                throw AuthResultCode.HIERARCHY_CYCLE.businessException(
                        "不能把用户组挂到自己的下级下面：" + parent.getId());
            }
            if (!seen.add(cursor.getId())) {
                return;
            }
            cursor = cursor.getParentId() == null
                    ? null : userGroupMapper.selectById(cursor.getParentId());
        }
    }

    /* ------------------------------------------------------------------ 组装 */

    private static void applyTo(UserGroupEntity entity, UserGroupSaveRequest request,
                                UserGroupEntity parent, String category) {
        entity.setGroupName(request.getGroupName().trim());
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setCategory(category);
        entity.setIsDefault(flag(request.getIsDefault(), 0, "isDefault"));
        entity.setIsEnabled(flag(request.getIsEnabled(), 1, "isEnabled"));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setDescription(FilterValue.text(request.getDescription()));
    }

    private static UserGroupVO toVO(UserGroupEntity entity) {
        UserGroupVO vo = new UserGroupVO();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setGroupCode(entity.getGroupCode());
        vo.setGroupName(entity.getGroupName());
        vo.setParentId(entity.getParentId());
        vo.setCategory(entity.getCategory());
        vo.setIsDefault(entity.getIsDefault());
        vo.setIsEnabled(entity.getIsEnabled());
        vo.setSortNo(entity.getSortNo());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 把关系行批量装配成出参。
     *
     * <p>名字按类型分组、各批量查一次（一共最多两条查询、与成员数量无关），
     * 不逐行回查（N+1）、也不用 JOIN —— 被加对象是多态的，
     * 一条 JOIN 得写 {@code CASE WHEN} 再挂两个 {@code LEFT JOIN}。
     * 详见 {@code UserGroupMemberVO} 的类注释。
     *
     * <p>{@code GROUP} 这一支当前不该有数据（接口已拒绝），但**必须留着**：
     * 表是多态的，历史上直接写库产生的行仍会出现在列表里，
     * 而"看到一条却没有名字"比"看到名字"更难排查。
     */
    private List<UserGroupMemberVO> toMemberVOs(List<RelUserGroupMemberEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> userNames = namesOfUser(rows.stream()
                .filter(row -> MemberType.USER.wireName().equals(row.getMemberType()))
                .map(RelUserGroupMemberEntity::getMemberId)
                .collect(Collectors.toSet()));
        Map<Long, String> groupNames = namesOfGroup(rows.stream()
                .filter(row -> MemberType.GROUP.wireName().equals(row.getMemberType()))
                .map(RelUserGroupMemberEntity::getMemberId)
                .collect(Collectors.toSet()));

        return rows.stream().map(row -> {
            MemberType type = MemberType.parse(row.getMemberType());
            Map<Long, String> names = type == MemberType.GROUP ? groupNames : userNames;
            return toMemberVO(row, type, names.get(row.getMemberId()));
        }).toList();
    }

    private Map<Long, String> namesOfUser(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (UserEntity user : userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                .in(UserEntity::getId, ids))) {
            names.put(user.getId(), displayNameOf(user));
        }
        return names;
    }

    private Map<Long, String> namesOfGroup(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (UserGroupEntity group : userGroupMapper.selectList(Wrappers.<UserGroupEntity>lambdaQuery()
                .in(UserGroupEntity::getId, ids))) {
            names.put(group.getId(), group.getGroupName());
        }
        return names;
    }

    /** displayName 可空（建号时不强制填），回退到 username —— 界面拿到空名字比拿到用户名更麻烦 */
    private static String displayNameOf(UserEntity user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    private static UserGroupMemberVO toMemberVO(RelUserGroupMemberEntity entity,
                                                MemberType memberType, String memberName) {
        UserGroupMemberVO vo = new UserGroupMemberVO();
        vo.setGrantId(entity.getId());
        vo.setMemberType(entity.getMemberType());
        vo.setMemberTypeLabel(memberType == null ? null : memberType.getLabel());
        vo.setMemberId(entity.getMemberId());
        vo.setMemberName(memberName);
        vo.setEffectiveFrom(entity.getEffectiveFrom());
        vo.setEffectiveTo(entity.getEffectiveTo());
        vo.setEffectiveNow(isWithin(entity.getEffectiveFrom(), entity.getEffectiveTo(),
                System.currentTimeMillis()));
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /** 把同键的其余行逻辑删除，只留下 {@code rows.get(0)} —— 见 {@link #bindMember} */
    private void revokeRedundantRows(List<RelUserGroupMemberEntity> rows) {
        for (int i = 1; i < rows.size(); i += 1) {
            RelUserGroupMemberEntity extra = rows.get(i);
            if (extra.getDeleted() == null || extra.getDeleted() == 0) {
                relUserGroupMemberMapper.deleteById(extra.getId());
            }
        }
    }

    /**
     * 撤销该组名下全部成员关系。
     *
     * <p>只有一个调用点：{@link #create} 的复活分支。它让"复活 = 空组"无条件成立。
     * 用 {@code delete(wrapper)} 而不是手写 SQL：它认 {@code @TableLogic}，
     * 翻成 {@code UPDATE ... SET deleted = 1 WHERE deleted = 0} —— 就地软删、不物理删除，
     * 因为"这个人曾经在这个组里、后来被移除"本身就是审计要回答的问题。
     */
    private void clearMembersOf(Long groupId) {
        relUserGroupMemberMapper.delete(Wrappers.<RelUserGroupMemberEntity>lambdaQuery()
                .eq(RelUserGroupMemberEntity::getUserGroupId, groupId));
    }

    /**
     * 撤销"这个组持有的全部角色"—— 即 {@code bw_auth_rel_role_member} 里
     * {@code member_type = 'GROUP'} 且指向本组的那些行。
     *
     * <p>两个调用点：{@link #create} 的复活分支（让复活 = 没有权限）与
     * {@link #delete}（成员清空后这些授权不再影响任何人）。
     *
     * <p>{@code memberType} 条件不能省：删掉它会把"与 ID 相同的用户"
     * 身上直接授予的角色一并撤销 —— 用户与组是两个独立的自增序列，会撞号。
     */
    private void clearRoleGrantsOf(Long groupId) {
        relRoleMemberMapper.delete(Wrappers.<RelRoleMemberEntity>lambdaQuery()
                .eq(RelRoleMemberEntity::getMemberType, MemberType.GROUP.wireName())
                .eq(RelRoleMemberEntity::getMemberId, groupId));
    }

    /**
     * 更新可变列 —— **逐列显式 {@code set}，不用 {@code updateById}**。
     *
     * <p>MyBatis-Plus 默认的 {@code NOT_NULL} 字段策略只把**非空**字段拼进 {@code SET}，
     * 于是"把某个字段改回 null"发不出去：影响 0 行、不报错、返回 200。
     * 本类上有两处会因此变成假功能：{@code parentId} 摘不回顶级、
     * {@code description} 清不掉。理由与写法与 {@code RoleService.updateColumns} 完全一致。
     *
     * <p>传空实体而不是 {@code null}：空实体本身不贡献任何 {@code SET}，
     * 但它让 MyBatis-Plus 的 {@code updateFill} 有机会跑起来，
     * 否则 {@code update_time / update_by} 会停更 —— 而"这条记录上次改于何时"
     * 正是审计要回答的问题。
     */
    private void updateColumns(UserGroupEntity entity) {
        userGroupMapper.update(new UserGroupEntity(), Wrappers.<UserGroupEntity>lambdaUpdate()
                .eq(UserGroupEntity::getId, entity.getId())
                .set(UserGroupEntity::getGroupName, entity.getGroupName())
                .set(UserGroupEntity::getParentId, entity.getParentId())
                .set(UserGroupEntity::getCategory, entity.getCategory())
                .set(UserGroupEntity::getIsDefault, entity.getIsDefault())
                .set(UserGroupEntity::getIsEnabled, entity.getIsEnabled())
                .set(UserGroupEntity::getSortNo, entity.getSortNo())
                .set(UserGroupEntity::getDescription, entity.getDescription()));
    }

    /* ------------------------------------------------------------------ 枚举与归一 */

    /**
     * 分组方式：写入时严格。
     *
     * <p>拒绝 {@code DYNAMIC} 而不是"存下来但不用"，理由与拒绝 {@code GROUP} 成员同源：
     * 一个动态组会永远算不出成员，而界面上的规则框看起来一切正常 ——
     * 配置的人会反复检查那条规则，却不会想到"这个功能根本没实现"。
     * 一句 40001 比一个沉默的空组省下的时间多得多。
     */
    private static String resolveCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return CATEGORY_STATIC;
        }
        if (!CATEGORY_STATIC.equalsIgnoreCase(raw.trim())) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "用户组当前只支持 STATIC（成员手工维护）："
                            + "DYNAMIC 的动态成员规则求值尚未实现，接受它会得到一个永远算不出成员的组");
        }
        return CATEGORY_STATIC;
    }

    /** 写入时严格：类型不合法就报错，并把合法取值列进文案 */
    private static MemberType resolveMemberType(String raw) {
        MemberType parsed = MemberType.parse(raw);
        if (parsed == null) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "成员类型不合法，取值：" + Arrays.stream(MemberType.values())
                            .map(MemberType::wireName)
                            .collect(Collectors.joining(" / ")));
        }
        return parsed;
    }

    /**
     * 三态标志位只接受 0 / 1 —— 理由同 {@code RoleService.flag}：
     * 放行别的值不会报错，只会静默改变语义，而这两个开关现在都会进判定。
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

    /**
     * 成员与用户组必须同租户。
     *
     * <p>文案里点名"不属于同一租户"而不是笼统的"不存在"：{@code 40002} 的默认文案是
     * "引用的关联数据不存在"，而这里数据**确实存在**，只是放错了租户。
     * 只说"不存在"会让调用方去查主键有没有写错，方向完全跑偏 ——
     * 判定 SQL 两侧都限定 {@code tenant_id}，跨租户的组成员关系**永远匹配不上**。
     */
    private static void assertSameTenant(Long groupTenantId, Long userTenantId, Long userId) {
        if (groupTenantId == null || userTenantId == null || !groupTenantId.equals(userTenantId)) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "该用户与用户组不属于同一租户（" + userId + "）—— "
                            + "判定两侧都限定租户，跨租户的组成员关系永远不会生效");
        }
    }

    /** 半开区间：[from, to)。两端都可空，都空表示长期有效 */
    private static Boolean isWithin(Long from, Long to, long now) {
        if (from != null && now < from) {
            return false;
        }
        return to == null || now < to;
    }

}
