package com.bluewhale.sts.auth.role;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.MemberType;
import com.bluewhale.sts.auth.common.enums.RoleSource;
import com.bluewhale.sts.auth.relation.RelRoleMemberEntity;
import com.bluewhale.sts.auth.relation.RelRoleMemberMapper;
import com.bluewhale.sts.auth.usergroup.UserGroupEntity;
import com.bluewhale.sts.auth.usergroup.UserGroupMapper;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import com.bluewhale.sts.common.basic.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色成员关系 —— **把角色授给用户或用户组**。
 *
 * <p>本类补上 RBAC 判定链配置侧的**最后一段**。在此之前三段的状况是：
 * 资源目录能造权限码（ADR-0021），角色能持有权限码（ADR-0022），
 * 判定能算出"某个人最终有哪些权限" —— 但**没有接口能把角色授给任何人**。
 * admin 之所以有权限，是因为 {@code DefaultAdminSeeder} 直接写了一条成员关系
 * （{@code grantRole}）；{@code RoleApiTest} 验证判定链路时也只能绕开接口、
 * 直接经 Mapper 插一行。也就是说：成员关系是整条链上唯一"存在、参与判定、
 * 却无法通过 HTTP 配置"的一环。
 *
 * <p>它也是 {@code RoleService} 类注释里那句"本类只回答'这个角色有哪些权限'，
 * 不回答'谁有这个角色'"的另一半。分开两个类而不是继续往 {@code RoleService} 里加，
 * 是因为这两个问题**共用一张表却没有任何共同逻辑**：授权管的是
 * {@code bw_auth_rel_role_resource}，成员管的是 {@code bw_auth_rel_role_member}，
 * 它们的校验对象、生命周期约束、乃至"是否需要分页"都不一样
 * （资源被权限码总量封顶，成员没有上限）。合在一起只会让两个问题互相干扰。
 *
 * <p><b>四条比资源授权更严的约束：</b>
 * <ul>
 *   <li><b>成员类型不设默认值。</b>{@code member_id} 只在它自己所指向的那张表里有意义 ——
 *       用户与用户组是两个独立的自增序列，撞号是常态。默认 {@code USER} 会让
 *       "想授用户组但漏写了类型"变成"悄悄授给了一个恰好同号的用户"，
 *       而且请求里没有任何一处写错过。详见 {@link RoleMemberBindRequest}。</li>
 *   <li><b>成员必须存在</b>（{@code USER} → 用户表，{@code GROUP} → 用户组表），
 *       不存在返 40002。多态表**刻意没有外键**（见 {@code MemberType} 的注释：
 *       放弃外键换来"我为什么有权限"的追溯只有一条路径），存在性因此只能由应用层保证。</li>
 *   <li><b>成员必须与角色同租户</b>，否则返 40002。判定 SQL 两侧都限定
 *       {@code tenant_id}（{@code rm.tenant_id} 与 {@code ro.tenant_id}），
 *       跨租户的成员关系**永远匹配不上** —— 它会安静地躺在库里，
 *       列表里看得见、权限里没有。这种"静默不可达的数据"正是本仓尽量不让接口制造的东西，
 *       与 {@code RoleService.resolveParent} 校验"父角色同租户"是同一条。</li>
 *   <li><b>授予是 upsert</b>：同一个 (角色, 成员类型, 成员) 只保留一条有效行。
 *       表上没有这个三元组的唯一索引，所以还要清掉同键的其余行 —— 与资源授权同一个理由，
 *       见 {@link #bind}。</li>
 * </ul>
 *
 * <p><b>本类有两个方向相反的查询，都在这里是有意的：</b>从角色看成员
 * （{@link #page}，"这个角色给了谁"）与从人看角色（{@link #rolesOfUser}，
 * "这个人的角色是哪来的"）。两者共用同一张表、同一套"直接授予 / 经组授予"的分支，
 * 分开放会让那段分支逻辑出现两份，而它们的**语义必须一致** ——
 * 一份改了另一份没改的表现是"角色列表里能看到组，反查却说没有"。
 * HTTP 侧 {@code rolesOfUser} 挂在 {@code /api/auth/users/{id}/roles}
 * （见 {@code UserController}），而不是挂在 {@code /api/auth/roles} 下：
 * 路径要跟着"这句话的主语"走，服务放哪个包是代码组织问题，两件事不必一致。
 *
 * <p><b>不拒绝给"已停用的角色"或"已停用的用户"加成员。</b>它们当前都不产生权限
 * （判定要求 {@code ro.status = 'ENABLED'}，成员自身可用性由认证环节把关），
 * 但"先配置、后启用"是合理的运维顺序：新员工的账号与角色一起建好、上线当天再启用，
 * 比上线当天手忙脚乱地补配置安全得多。这与 {@code RoleService.grant} 不拒绝
 * 给已停用资源授权是同一条判断。
 */
@Service
public class RoleMemberService {

    private final RoleMapper roleMapper;
    private final RelRoleMemberMapper relRoleMemberMapper;
    private final UserMapper userMapper;
    private final UserGroupMapper userGroupMapper;

    public RoleMemberService(RoleMapper roleMapper,
                             RelRoleMemberMapper relRoleMemberMapper,
                             UserMapper userMapper,
                             UserGroupMapper userGroupMapper) {
        this.roleMapper = roleMapper;
        this.relRoleMemberMapper = relRoleMemberMapper;
        this.userMapper = userMapper;
        this.userGroupMapper = userGroupMapper;
    }

    /* ------------------------------------------------------------------ 查询 */

    /**
     * 分页查询角色成员，可按成员类型过滤。
     *
     * <p>分页而不是一次取完，理由见 {@link RoleMemberQuery} 的类注释：
     * 一个角色能授出的资源数被权限码总量封顶，成员却没有上限。
     *
     * <p>过滤值先取局部变量再进 {@code eq}：MyBatis-Plus 的
     * {@code eq(condition, 列, 值)} 里那个"值"是**无条件求值**的，
     * 写成 {@code query.getX().trim()} 会在值为 null 时当场 NPE，
     * 外层的条件参数救不了。这条坑在本仓已经踩过一次。
     */
    @Transactional(readOnly = true)
    public PageResult<RoleMemberVO> page(Long roleId, RoleMemberQuery query) {
        requireRole(roleId);
        String memberType = normalizeMemberTypeFilter(query.getMemberType());

        Page<RelRoleMemberEntity> page = relRoleMemberMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<RelRoleMemberEntity>lambdaQuery()
                        .eq(RelRoleMemberEntity::getRoleId, roleId)
                        .eq(memberType != null, RelRoleMemberEntity::getMemberType, memberType)
                        // 只按主键排：授出顺序就是排查时想看的顺序，且主键单调，翻页稳定
                        .orderByAsc(RelRoleMemberEntity::getId));

        return PageResult.of(toVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getSize());
    }

    /* ------------------------------------------------------------------ 授予与撤销 */

    /**
     * 把角色授给一个成员（用户或用户组）。
     *
     * <p><b>upsert 而不是"直接插"或"先删后插"</b>，与 {@code RoleService.grant} 同一套理由：
     * <ul>
     *   <li>直接插会在"把临时代理改回长期有效"时留下两条同键的行。判定取并集，
     *       多条无害，但读表的人再也说不清这个人是什么时候被加进来的；</li>
     *   <li>先删后插会让 {@code create_time} 每次都刷新，而审计面板只记变更后的值，
     *       "这段成员关系从什么时候开始"就追溯不到了。</li>
     * </ul>
     * 因此按 (角色, 成员类型, 成员主键) 定位唯一一条有效行为目标：存在则改它的时间窗，
     * 存在但已被解除则**复活**它，不存在才新建。**并且把同键的其余行全部逻辑删除** ——
     * 表上没有这个三元组的唯一索引，服务层的"先查后写"挡不住并发，
     * 残留的重复行会让一段历史成员关系复活。
     *
     * <p>时间窗的写入用**逐列显式 {@code set}**，不用 {@code updateById}：
     * MyBatis-Plus 默认的 {@code NOT_NULL} 字段策略不会把 {@code null} 拼进 {@code SET}，
     * 于是"把临时代理改成长期有效"（= 清掉时间窗）会**影响 0 行且不报错**，
     * 表现为"我明明改回来了，怎么权限还是个错的"。授权侧已经因此付出过一次代价，
     * 见 {@code RoleService.updateColumns}。
     */
    @Transactional
    public RoleMemberVO bind(Long roleId, RoleMemberBindRequest request) {
        RoleEntity role = requireRole(roleId);
        MemberType memberType = resolveMemberType(request.getMemberType());
        MemberRef member = requireMember(memberType, request.getMemberId(), role.getTenantId());

        Long from = request.getEffectiveFrom();
        Long to = request.getEffectiveTo();
        if (from != null && to != null && from >= to) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "生效区间的起始时间必须早于结束时间，否则这段成员关系永远不会生效");
        }

        List<RelRoleMemberEntity> existing = relRoleMemberMapper
                .selectAnyByRoleAndMember(roleId, memberType.wireName(), member.id());
        if (existing.isEmpty()) {
            RelRoleMemberEntity created = new RelRoleMemberEntity();
            // tenantId 取角色的，不是成员的 —— 两者已校验相等，用角色的那个表达的是
            // "这段关系属于角色所在的租户"，与判定 SQL 的 ro.tenant_id 限定对齐
            created.setTenantId(role.getTenantId());
            created.setRoleId(roleId);
            created.setMemberId(member.id());
            created.setMemberType(memberType.wireName());
            created.setEffectiveFrom(from);
            created.setEffectiveTo(to);
            relRoleMemberMapper.insert(created);
            return toVO(created, memberType, member.name());
        }

        RelRoleMemberEntity target = existing.get(0);
        revokeRedundantRows(existing);
        if (target.getDeleted() != null && target.getDeleted() != 0) {
            relRoleMemberMapper.restoreDeletedById(target.getId());
            // 内存里的 deleted 也要清掉：update 的 WHERE 带 deleted = 0，
            // 留着 1 会让后面这条 UPDATE 静默影响 0 行
            target.setDeleted(0);
        }
        target.setEffectiveFrom(from);
        target.setEffectiveTo(to);
        relRoleMemberMapper.update(new RelRoleMemberEntity(),
                Wrappers.<RelRoleMemberEntity>lambdaUpdate()
                        .eq(RelRoleMemberEntity::getId, target.getId())
                        .set(RelRoleMemberEntity::getEffectiveFrom, from)
                        .set(RelRoleMemberEntity::getEffectiveTo, to));
        return toVO(target, memberType, member.name());
    }

    /**
     * 解除一段成员关系（逻辑删除）。
     *
     * <p>解除前要求"确实持有"：对一个本来就没授过的成员返回成功，会让调用方
     * 以为"已经撤掉了"，而真相是从来没授过 —— 两种情况在前端看起来一样，
     * 排查"为什么这个人还能进"时方向会被带偏。返回 40408 让差异可见。
     * 与 {@code RoleService.revoke} 返回 40407 是同一条规则用在另一条边上。
     *
     * <p><b>逐段解除，没有"清空某个角色的全部成员"这种接口。</b>那一次点击可能让
     * 几百人同时失去权限，而审计里只有一条记录 —— 正是本仓尽力避免的
     * "改了配置却没人知道改了哪些"。
     */
    @Transactional
    public void revoke(Long roleId, String rawMemberType, Long memberId) {
        requireRole(roleId);
        MemberType memberType = resolveMemberType(rawMemberType);

        List<RelRoleMemberEntity> rows = relRoleMemberMapper
                .selectAnyByRoleAndMember(roleId, memberType.wireName(), memberId);
        List<RelRoleMemberEntity> active = rows.stream()
                .filter(row -> row.getDeleted() == null || row.getDeleted() == 0)
                .toList();
        if (active.isEmpty()) {
            throw AuthResultCode.ROLE_MEMBER_GRANT_NOT_FOUND.businessException();
        }
        active.forEach(row -> relRoleMemberMapper.deleteById(row.getId()));
    }

    /* ------------------------------------------------------------------ 反查 */

    /**
     * 反查某个用户身上的角色**及其来源**（直接授予 / 经用户组授予）。
     *
     * <p>这是排查权限问题时该第一个打开的接口。在此之前，"这个人为什么有这个权限"
     * 只能顺着 {@code /api/auth/me} 给出的角色编码反查角色列表、再逐个打开成员列表 ——
     * 而"经用户组授予"的那条路在角色列表里根本不显形：角色上挂的是组，不是人。
     *
     * <p><b>它同时跨越两个来源，这正是必须有一个统一入口的原因。</b>
     * 直接授予与经组授予是两条边：{@code rel_role_member} 里
     * {@code member_type = 'USER'} 与 {@code member_type = 'GROUP'} 各占一半，
     * 调用方若自己拼，很容易只查了前者 —— 表现是"这个人明明有权限，
     * 反查却说他没有任何角色"。
     *
     * <p><b>不过滤状态与时间窗</b>，与 {@code RoleMemberVO} 同一条取舍：
     * 一行"角色已停用"或"组已停用"或"授权已过期"是**答案的一部分**，
     * 而不是噪声。因此这里只补派生标记（{@code effectiveNow} /
     * {@code groupMemberEffectiveNow}），不把行滤掉、也不把多个开关压成一个布尔。
     * 唯一被真正过滤掉的是"用户根本不在那个组里"——{@link RoleMapper#selectUserRoleSources}
     * 用 {@code gm.id IS NOT NULL} 表达，因为那种情况下这条角色压根不是他的。
     *
     * <p>组的名字与启用状态**批量查一次**再回填，不逐行回查（N+1）：
     * 一个用户身上的组通常是个位数，但接口的查询次数应当与数据量无关。
     *
     * <p>权限落在 {@code auth:user:read} 上：它读的是"某个用户"的信息，
     * 与用户列表 / 用户详情同类。**没有单设 {@code auth:user:role:read}** ——
     * 能看用户的人本来就能看到他的角色（角色本身还另有 {@code auth:role:read} 保护，
     * 但反查返回的只是"角色名 + 来源"，不含该角色的权限集合）。
     */
    @Transactional(readOnly = true)
    public List<UserRoleVO> rolesOfUser(Long userId) {
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw AuthResultCode.USER_NOT_FOUND.businessException();
        }

        long now = System.currentTimeMillis();
        List<UserRoleVO> rows = roleMapper.selectUserRoleSources(user.getTenantId(), userId);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, UserGroupEntity> groups = groupsOf(rows);
        for (UserRoleVO row : rows) {
            boolean viaGroup = RoleSource.GROUP.wireName().equals(row.getSource());
            row.setSourceLabel(viaGroup
                    ? RoleSource.GROUP.getLabel() : RoleSource.DIRECT.getLabel());
            row.setEffectiveNow(isWithin(row.getEffectiveFrom(), row.getEffectiveTo(), now));
            if (!viaGroup) {
                continue;
            }
            UserGroupEntity group = groups.get(row.getGroupId());
            // 组已被删除时名字为 null 而不是整行过滤掉：一段"曾经通过某个组拿到角色"的历史
            // 恰恰是排查"为什么权限对不上"时要看的东西（同 RoleMemberVO 的 memberName）
            row.setGroupName(group == null ? null : group.getGroupName());
            row.setGroupEnabled(group == null ? null : group.getIsEnabled());
            row.setGroupMemberEffectiveNow(isWithin(row.getGroupMemberEffectiveFrom(),
                    row.getGroupMemberEffectiveTo(), now));
        }
        return rows;
    }

    /** 批量取反查结果里出现过的用户组，供回填组名与启用状态 */
    private Map<Long, UserGroupEntity> groupsOf(List<UserRoleVO> rows) {
        Set<Long> ids = rows.stream()
                .map(UserRoleVO::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserGroupEntity> groups = new LinkedHashMap<>();
        for (UserGroupEntity group : userGroupMapper.selectList(
                Wrappers.<UserGroupEntity>lambdaQuery().in(UserGroupEntity::getId, ids))) {
            groups.put(group.getId(), group);
        }
        return groups;
    }

    /* ------------------------------------------------------------------ 取数与校验 */

    private RoleEntity requireRole(Long id) {
        RoleEntity entity = id == null ? null : roleMapper.selectById(id);
        if (entity == null) {
            throw AuthResultCode.ROLE_NOT_FOUND.businessException();
        }
        return entity;
    }

    /**
     * 校验成员存在且与角色同租户，并顺手取出它的名字。
     *
     * <p>把"存在性 + 租户一致 + 取名字"收在一次调用里，是因为这三件事都必须
     * 在同一个对象上做，拆成三次查询等于查同一个 id 三遍。
     */
    private MemberRef requireMember(MemberType memberType, Long memberId, Long roleTenantId) {
        if (memberId == null) {
            throw AuthResultCode.PARAM_INVALID.businessException("成员主键不能为空");
        }
        return switch (memberType) {
            case USER -> {
                UserEntity user = userMapper.selectById(memberId);
                if (user == null) {
                    throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                            "引用的用户不存在：" + memberId);
                }
                assertSameTenant(roleTenantId, user.getTenantId(), "用户", memberId);
                // displayName 可空（建号时不强制填），回退到 username ——
                // 界面拿到一个空名字比拿到一个"看起来像用户名的名字"更麻烦
                String name = user.getDisplayName() == null || user.getDisplayName().isBlank()
                        ? user.getUsername() : user.getDisplayName();
                yield new MemberRef(user.getId(), name);
            }
            case GROUP -> {
                UserGroupEntity group = userGroupMapper.selectById(memberId);
                if (group == null) {
                    throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                            "引用的用户组不存在：" + memberId);
                }
                assertSameTenant(roleTenantId, group.getTenantId(), "用户组", memberId);
                yield new MemberRef(group.getId(), group.getGroupName());
            }
        };
    }

    /**
     * 成员与角色必须同租户。
     *
     * <p>文案里点名"永远匹配不上"，是因为这条约束从错误码上看不出来 ——
     * 40002 的默认文案是"引用的关联数据不存在"，而这里数据**确实存在**，
     * 只是放错了租户。只说"不存在"会让调用方去查主键有没有写错，方向完全跑偏。
     */
    private static void assertSameTenant(Long roleTenantId, Long memberTenantId,
                                         String label, Long memberId) {
        if (roleTenantId == null || memberTenantId == null
                || !roleTenantId.equals(memberTenantId)) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "该" + label + "与角色不属于同一租户（" + memberId + "）—— "
                            + "判定两侧都限定租户，跨租户的成员关系永远不会生效");
        }
    }

    /* ------------------------------------------------------------------ 组装 */

    /**
     * 把关系行批量装配成出参。
     *
     * <p><b>名字分两次批量查，不逐行回查（N+1），也不用 JOIN。</b>
     * 被授对象是多态的，一条 JOIN 得写 {@code CASE WHEN} 再挂两个 {@code LEFT JOIN}，
     * 一条 SQL 同时被两张表的方言细节影响；而按类型分组后各查一次
     * {@code IN}，总共 3 条查询、与成员数量无关，每一条都能单独看懂。
     * 详见 {@link RoleMemberVO} 的类注释。
     */
    private List<RoleMemberVO> toVOs(List<RelRoleMemberEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> userNames = namesOfUser(rows.stream()
                .filter(row -> MemberType.USER.wireName().equals(row.getMemberType()))
                .map(RelRoleMemberEntity::getMemberId)
                .collect(Collectors.toSet()));
        Map<Long, String> groupNames = namesOfGroup(rows.stream()
                .filter(row -> MemberType.GROUP.wireName().equals(row.getMemberType()))
                .map(RelRoleMemberEntity::getMemberId)
                .collect(Collectors.toSet()));

        return rows.stream().map(row -> {
            MemberType type = MemberType.parse(row.getMemberType());
            Map<Long, String> names = type == MemberType.GROUP ? groupNames : userNames;
            return toVO(row, type, names.get(row.getMemberId()));
        }).toList();
    }

    private Map<Long, String> namesOfUser(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (UserEntity user : userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                .in(UserEntity::getId, ids))) {
            names.put(user.getId(), user.getDisplayName() == null || user.getDisplayName().isBlank()
                    ? user.getUsername() : user.getDisplayName());
        }
        return names;
    }

    private Map<Long, String> namesOfGroup(Collection<Long> ids) {
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

    private static RoleMemberVO toVO(RelRoleMemberEntity entity, MemberType memberType, String memberName) {
        RoleMemberVO vo = new RoleMemberVO();
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

    /** 把同键的其余行逻辑删除，只留下 {@code rows.get(0)} —— 见 {@link #bind} */
    private void revokeRedundantRows(List<RelRoleMemberEntity> rows) {
        for (int i = 1; i < rows.size(); i += 1) {
            RelRoleMemberEntity extra = rows.get(i);
            if (extra.getDeleted() == null || extra.getDeleted() == 0) {
                relRoleMemberMapper.deleteById(extra.getId());
            }
        }
    }

    /* ------------------------------------------------------------------ 枚举与归一 */

    /** 写入时严格：类型不合法就报错，并把合法取值列进文案（与 {@code RoleService.resolveDataScope} 一致） */
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
     * 过滤时宽松：认得就归一成标准写法（{@code user} → {@code USER}），
     * 认不得就原样拿去比 —— 过滤条件不该让查询失败，结果为空比弹错更接近预期。
     */
    private static String normalizeMemberTypeFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        MemberType parsed = MemberType.parse(raw);
        return parsed == null ? raw.trim() : parsed.wireName();
    }

    /** 半开区间：[from, to)。两端都可空，都空表示长期有效 */
    private static Boolean isWithin(Long from, Long to, long now) {
        if (from != null && now < from) {
            return false;
        }
        return to == null || now < to;
    }

    /** 成员的存在性校验结果，顺带把名字带出来，避免同一行查两遍 */
    private record MemberRef(Long id, String name) {
    }

}
