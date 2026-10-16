package com.bluewhale.sts.auth.organization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.relation.RelOrgUserEntity;
import com.bluewhale.sts.auth.relation.RelOrgUserMapper;
import com.bluewhale.sts.auth.tenant.TenantService;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织成员关系服务 —— **把用户挂到组织下**，以及主岗的归属。
 *
 * <p>它补上的是"数据权限坐标系"的两半中缺的那一半：{@code OrganizationService}
 * 管树的形状，本类管"**人在树的哪个节点上**"。{@code DEPT} 档位问的正是后者 ——
 * 一棵没有人的树，无论多整齐都算不出任何范围。
 *
 * <p><b>主岗是本类唯一影响权限的字段，因此它有三条硬规则：</b>
 * <ol>
 *   <li><b>每人每租户至多一个主岗。</b>没设主岗的人 DEPT 档位算不出范围、
 *       只能往窄兜底成"仅本人"；而设了两个主岗则让"本部门"变成"两个部门"，
 *       等于悄悄放大了范围。两个方向都不报错，所以只能靠这里的规则挡住。
 *       数据库没有唯一约束（见 {@code RelOrgUserMapper} 的注释），
 *       唯一性完全由 {@link #applyPrimaryPosition} 保证；</li>
 *   <li><b>不静默改主岗。</b>给一个已有主岗的人新增归属时，不传
 *       {@code isPrimaryPosition} 不会把主岗搬过去 —— 那会悄悄改变他的数据范围，
 *       而他的部门归属看起来毫无变化。搬主岗必须显式，并且有自己的审计动作；</li>
 *   <li><b>时态参与求值。</b>{@code effective_from / effective_to} 过期的归属
 *       **不算数**（见 {@link #primaryOrganizationOf}）。这是本仓已修过的第五处
 *       "开关在表里、判定却不读它"的同类缺陷 —— 时态列从建库起就在这里，
 *       若求值时不读，一段"临时借调三个月"的归属会变成永久授权。</li>
 * </ol>
 *
 * <p><b>不拒绝把人挂到已停用的组织下</b>，与"不拒绝给已停用角色加成员"同一条：
 * "先配置、后启用"是合理的运维顺序（新部门的人员先挂好、上线当天再启用）。
 * 差别在于停用的组织**暂时不参与数据范围求值**，这是可解释的、也不会放行任何东西。
 * （注意这与 {@code OrganizationService.resolveParent} 拒绝把**子组织**挂到停用组织下
 * 是相反方向的判断，后者影响的是整棵子树的路径，不是一个开关能纠正回来的。）
 */
@Service
public class OrganizationMemberService {

    private final OrganizationService organizationService;
    private final RelOrgUserMapper relOrgUserMapper;
    private final UserMapper userMapper;
    private final TenantService tenantService;

    public OrganizationMemberService(OrganizationService organizationService,
                                     RelOrgUserMapper relOrgUserMapper,
                                     UserMapper userMapper,
                                     TenantService tenantService) {
        this.organizationService = organizationService;
        this.relOrgUserMapper = relOrgUserMapper;
        this.userMapper = userMapper;
        this.tenantService = tenantService;
    }

    /* ------------------------------------------------------------------ 读 */

    @Transactional(readOnly = true)
    public PageResult<OrgMemberVO> pageMembers(Long orgId, OrgMemberQuery query) {
        organizationService.requireOrg(orgId);

        Page<RelOrgUserEntity> page = relOrgUserMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<RelOrgUserEntity>lambdaQuery()
                        .eq(RelOrgUserEntity::getOrganizationId, orgId)
                        .eq(Boolean.TRUE.equals(query.getPrimaryOnly()),
                                RelOrgUserEntity::getIsPrimaryPosition, 1)
                        // 只按主键排：加入顺序就是排查时想看的顺序，且主键单调、翻页稳定
                        .orderByAsc(RelOrgUserEntity::getId));

        return PageResult.of(toMemberVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getSize());
    }

    /**
     * 反查：某个用户属于哪些组织。
     *
     * <p><b>不分页</b>，与"组织成员分页"是有意的差别：一个人的归属是几条到十几条的量级，
     * 分页只会让调用方多写一层循环；而一个组织的成员没有上限，那边必须分页。
     * 两者的边界不同，就不该套同一个形状。
     *
     * <p>返回**全部**归属（含已过期、非主岗的），每一行都带着
     * {@code effectiveNow} 与 {@code orgStatus} —— 这是排查
     * "他的数据范围为什么不对"时最需要看到的两列：过期与停用都会让主岗失效，
     * 而只返回"有效的那一条"会把原因藏起来。
     */
    @Transactional(readOnly = true)
    public List<UserOrgVO> listByUser(Long userId) {
        Long tenantId = AuthContextHolder.require().getTenantId();
        // 用户本身也要过可见性：此前这里只按当前租户过滤归属行 ⇒ 跨租户查一个别的用户
        // 会得到"他在本租户没有归属"这个**安静但错误的**答案（正确的答案是他不存在）。
        // 这一处本来是安全形态（返回空列表、不泄漏数据），改它是为了一致性：
        // 同族入口对"不存在"与"不属于当前租户"必须给同一个答案（decisions/0074）
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw AuthResultCode.USER_NOT_FOUND.businessException();
        }
        tenantService.assertVisible(user.getTenantId(), AuthResultCode.USER_NOT_FOUND);
        List<RelOrgUserEntity> rows = relOrgUserMapper.selectList(
                Wrappers.<RelOrgUserEntity>lambdaQuery()
                        .eq(RelOrgUserEntity::getTenantId, tenantId)
                        .eq(RelOrgUserEntity::getUserId, userId)
                        .orderByAsc(RelOrgUserEntity::getId));
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, OrganizationEntity> orgs = new LinkedHashMap<>();
        for (OrganizationEntity org : organizationService.listByIds(orgIdsOf(rows))) {
            orgs.put(org.getId(), org);
        }
        return rows.stream().map(row -> toUserOrgVO(row, orgs.get(row.getOrganizationId()))).toList();
    }

    /**
     * 求值入口：某个用户**当前有效的主岗所在组织**，供数据范围求值使用。
     *
     * <p>四个条件缺一不可，每一个都对应一种"看起来没问题、其实算错"的情形：
     * <ol>
     *   <li>{@code isPrimaryPosition = 1} —— 非主岗的归属不参与（"本部门"取主岗，
     *       取全部所属部门会让部门一多、能看的数据反而超标）；</li>
     *   <li><b>时态在区间内</b> —— 过期的归属不算数。判定放在 SQL 里（而不是取回来再过滤），
     *       是为了让"过期的行根本不进入内存"这件事可以被一眼看出来；</li>
     *   <li>组织未被删除 —— {@code selectById} 自动带上 {@code deleted = 0}；</li>
     *   <li><b>组织处于启用状态</b> —— 停用的组织不再能作为范围目标。
     *       这一条与"停用不级联"配套：停用上级部门不影响下级各自的有效性，
     *       所以这里只需检查主岗那个组织自己。</li>
     * </ol>
     *
     * <p>返回 {@code null} 是**正常结果**，不是错误：一个人可以没有组织归属、
     * 或者主岗所在部门被停用了。调用方必须把 null 当作"算不出精确范围"，
     * 往**窄**的档位兜底 —— 反过来（null 就当全部）会让"配了一个还没生效的范围"
     * 变成一次静默越权。
     */
    @Transactional(readOnly = true)
    public OrganizationEntity primaryOrganizationOf(long userId, long tenantId) {
        long now = System.currentTimeMillis();
        List<RelOrgUserEntity> rows = relOrgUserMapper.selectList(
                Wrappers.<RelOrgUserEntity>lambdaQuery()
                        .eq(RelOrgUserEntity::getTenantId, tenantId)
                        .eq(RelOrgUserEntity::getUserId, userId)
                        .eq(RelOrgUserEntity::getIsPrimaryPosition, 1)
                        .and(w -> w.isNull(RelOrgUserEntity::getEffectiveFrom)
                                .or().le(RelOrgUserEntity::getEffectiveFrom, now))
                        .and(w -> w.isNull(RelOrgUserEntity::getEffectiveTo)
                                .or().gt(RelOrgUserEntity::getEffectiveTo, now))
                        .orderByAsc(RelOrgUserEntity::getId));
        if (rows.isEmpty()) {
            return null;
        }
        OrganizationEntity org = organizationService.requireOrgOrNull(rows.get(0).getOrganizationId());
        if (org == null || Status.DISABLED.wireName().equalsIgnoreCase(org.getStatus())) {
            return null;
        }
        return org;
    }

    /* ------------------------------------------------------------------ 写 */

    /**
     * 把用户挂到组织下（upsert）。
     *
     * <p>upsert 而不是"直接插"或"先删后插"，与 {@code UserGroupService.bindMember}
     * 同一套理由：直接插会在"把临时借调改回长期"时留下两条行；先删后插会让
     * {@code create_time} 每次刷新，这段归属"从什么时候开始"就追溯不到了。
     * 因此按 (组织, 用户) 定位唯一一条有效行为目标：存在则改它的时间窗与岗位信息，
     * 存在但已被移除则**复活**它，不存在才新建。**并把同键的其余行全部逻辑删除** ——
     * 表上没有这个二元组的唯一索引（见 {@code RelOrgUserMapper}），
     * 服务层的"先查后写"挡不住并发，残留的重复行会让一段已移除的历史归属复活。
     *
     * <p>主岗的处理见 {@link #applyPrimaryPosition}。
     */
    @Transactional
    public OrgMemberVO bindMember(Long orgId, OrgMemberBindRequest request) {
        OrganizationEntity org = organizationService.requireOrg(orgId);
        UserEntity user = requireSameTenantUser(org.getTenantId(), request.getMemberId());

        Long from = request.getEffectiveFrom();
        Long to = request.getEffectiveTo();
        assertWindow(from, to);

        List<RelOrgUserEntity> existing = relOrgUserMapper.selectAnyByOrgAndUser(orgId, user.getId());
        RelOrgUserEntity target;
        if (existing.isEmpty()) {
            target = new RelOrgUserEntity();
            // tenantId 取组织的，不是用户的 —— 两者已校验相等，用组织的那个表达的是
            // "这段归属属于组织所在的租户"，与判定侧的 tenant_id 限定对齐
            target.setTenantId(org.getTenantId());
            target.setOrganizationId(orgId);
            target.setUserId(user.getId());
            relOrgUserMapper.insert(target);
        } else {
            target = existing.get(0);
            revokeRedundantRows(existing);
            if (target.getDeleted() != null && target.getDeleted() != 0) {
                relOrgUserMapper.restoreDeletedById(target.getId());
                // 内存里的 deleted 也要清掉：update 的 WHERE 带 deleted = 0，留着 1 会静默影响 0 行
                target.setDeleted(0);
            }
        }

        Integer desiredPrimary = resolveDesiredPrimary(target, user.getId(), org.getTenantId(),
                request.getIsPrimaryPosition());
        updateColumns(target, request, from, to);
        applyPrimaryPosition(target, user.getId(), org.getTenantId(), desiredPrimary);

        target.setIsPrimaryPosition(desiredPrimary);
        return toMemberVO(target, user);
    }

    /**
     * 把一个用户移出组织（逻辑删除）。
     *
     * <p>移除前要求"确实在这个组织里"（{@code 40410}）：对一个本来就不在的人返回成功，
     * 会让调用方以为"已经移掉了"，而真相是从来没挂过 —— 两种情况在前端看起来一样，
     * 排查"为什么这个人还看得见"时方向会被带偏。
     *
     * <p><b>移除主岗不会自动把主岗转给别人。</b>转给谁是个人事决定，服务端猜不出来；
     * 而随便挑一个（比如"最早加入的那个"）会让一次数据范围的变化
     * 出现在一个与该决定无关的人身上。移除后此人暂时没有主岗，
     * {@code DEPT} 档位往窄兜底成"仅本人"—— 保守方向，并且是**可解释的**
     * （反查接口会显示原因）。
     */
    @Transactional
    public void revokeMember(Long orgId, Long userId) {
        organizationService.requireOrg(orgId);
        List<RelOrgUserEntity> active = activeRows(orgId, userId);
        if (active.isEmpty()) {
            throw AuthResultCode.ORG_MEMBER_NOT_FOUND.businessException();
        }
        active.forEach(row -> relOrgUserMapper.deleteById(row.getId()));
    }

    /**
     * 把某人在某组织下的归属设为主岗。
     *
     * <p>独立接口、独立审计动作，理由是本仓最强调的一条：**它直接改变这个人的数据范围**。
     * 不改变任何计数（归属条数不变）、界面上只是多了一个标记，
     * 但 {@code DEPT} 档位取的就是主岗所在部门 —— 搬主岗等于换一个人能看到的部门。
     * "谁在什么时候把某人的主岗改到了哪个部门"必须能一条过滤直接命中，
     * 所以它不能藏在 {@code bindMember} 的那次 upsert 里。
     *
     * <p>要求先有归属：主岗是那段归属的一个属性，关系不存在时它无处安放 ——
     * 那种情况返回 {@code 40410}（先建立归属），而不是悄悄新建一条。
     */
    @Transactional
    public OrgMemberVO setPrimaryPosition(Long orgId, Long userId) {
        OrganizationEntity org = organizationService.requireOrg(orgId);
        List<RelOrgUserEntity> active = activeRows(orgId, userId);
        if (active.isEmpty()) {
            throw AuthResultCode.ORG_MEMBER_NOT_FOUND.businessException();
        }
        RelOrgUserEntity target = active.get(0);
        applyPrimaryPosition(target, userId, org.getTenantId(), 1);
        target.setIsPrimaryPosition(1);
        return toMemberVO(target, userMapper.selectById(userId));
    }

    /* ------------------------------------------------------------------ 主岗规则 */

    /**
     * 决定这次写入要不要把归属设成主岗。
     *
     * <p>三种情形，每种都写清楚，因为它们的差别正是本类最容易被改坏的地方：
     * <ul>
     *   <li><b>显式传了值</b> —— 听调用方的（传 0 就是把主岗从这个组织摘掉，
     *       此人可能因此没有主岗；这是显式意图，不拦。拦的是"没传却改了"）；</li>
     *   <li><b>没传，且此人当前没有别的主岗</b> —— 自动设为主岗。
     *       理由是没有主岗的人 DEPT 算不出范围、会被兜底成"仅本人"，
     *       而管理员刚把一个人挂进部门时，期望看到的是"他在这个部门"，
     *       不是"他挂进去了但什么也看不到"；</li>
     *   <li><b>没传，但此人已有主岗</b> —— 保持原样（即这次不设为主岗）。
     *       <b>绝不静默搬主岗。</b></li>
     * </ul>
     * 对已是主岗的那一行重复调用不会产生任何变化（幂等）。
     */
    private Integer resolveDesiredPrimary(RelOrgUserEntity target, Long userId, Long tenantId,
                                          Integer requested) {
        if (requested != null) {
            return flag(requested);
        }
        if (target.getIsPrimaryPosition() != null && target.getIsPrimaryPosition() == 1) {
            return 1;
        }
        return hasOtherPrimary(userId, tenantId, target.getId()) ? 0 : 1;
    }

    /**
     * 落地主岗：设为主岗时，先把**同租户内该用户的别处主岗全部降级**。
     *
     * <p>这是"每人至多一个主岗"唯一的实现点。数据库没有唯一约束，
     * 所以这条 update 就是全部保证 —— 漏了它，一个人会有两个主岗，
     * 而症状是"他能看到的数据比预期多"，不会报错。
     *
     * <p>用"先降级、再设置"而不是"先查有没有、有就报错"：报错会让
     * "把主岗从 A 部门搬到 B 部门"这个再正常不过的操作需要两步（先摘掉再设上），
     * 而中途失败会留下一个没有主岗的人。一次调用内完成才是一个原子的人事动作。
     *
     * <p>逐列显式 {@code set}，不用 {@code updateById}：字段策略不把 {@code null}
     * 拼进 {@code SET}，而这里写的都是非空值，用哪种都能生效 —— 统一成显式 set
     * 是为了让"这一列确实被写"在代码里看得见。
     */
    private void applyPrimaryPosition(RelOrgUserEntity target, Long userId, Long tenantId, int desired) {
        if (desired != 1) {
            demote(target.getId());
            return;
        }
        relOrgUserMapper.update(new RelOrgUserEntity(), Wrappers.<RelOrgUserEntity>lambdaUpdate()
                .eq(RelOrgUserEntity::getTenantId, tenantId)
                .eq(RelOrgUserEntity::getUserId, userId)
                .ne(RelOrgUserEntity::getId, target.getId())
                .set(RelOrgUserEntity::getIsPrimaryPosition, 0));
        relOrgUserMapper.update(new RelOrgUserEntity(), Wrappers.<RelOrgUserEntity>lambdaUpdate()
                .eq(RelOrgUserEntity::getId, target.getId())
                .set(RelOrgUserEntity::getIsPrimaryPosition, 1));
    }

    private void demote(Long id) {
        relOrgUserMapper.update(new RelOrgUserEntity(), Wrappers.<RelOrgUserEntity>lambdaUpdate()
                .eq(RelOrgUserEntity::getId, id)
                .set(RelOrgUserEntity::getIsPrimaryPosition, 0));
    }

    /** 除 {@code exceptId} 之外，此人当前是否还有别的主岗（**时态内**才算） */
    private boolean hasOtherPrimary(Long userId, Long tenantId, Long exceptId) {
        long now = System.currentTimeMillis();
        Long count = relOrgUserMapper.selectCount(Wrappers.<RelOrgUserEntity>lambdaQuery()
                .eq(RelOrgUserEntity::getTenantId, tenantId)
                .eq(RelOrgUserEntity::getUserId, userId)
                .eq(RelOrgUserEntity::getIsPrimaryPosition, 1)
                .ne(RelOrgUserEntity::getId, exceptId)
                .and(w -> w.isNull(RelOrgUserEntity::getEffectiveFrom)
                        .or().le(RelOrgUserEntity::getEffectiveFrom, now))
                .and(w -> w.isNull(RelOrgUserEntity::getEffectiveTo)
                        .or().gt(RelOrgUserEntity::getEffectiveTo, now)));
        return count != null && count > 0;
    }

    /* ------------------------------------------------------------------ 取数与组装 */

    private List<RelOrgUserEntity> activeRows(Long orgId, Long userId) {
        return relOrgUserMapper.selectList(Wrappers.<RelOrgUserEntity>lambdaQuery()
                .eq(RelOrgUserEntity::getOrganizationId, orgId)
                .eq(RelOrgUserEntity::getUserId, userId)
                .orderByAsc(RelOrgUserEntity::getId));
    }

    /**
     * 引用的人必须存在且同租户。
     *
     * <p>文案里点名"不属于同一租户"而不是笼统的"不存在"：{@code 40002} 的默认文案是
     * "引用的关联数据不存在"，而这里数据**确实存在**，只是放错了租户。
     * 判定两侧都限定 {@code tenant_id}，跨租户的归属**永远算不出范围**。
     */
    private UserEntity requireSameTenantUser(Long orgTenantId, Long userId) {
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "引用的用户不存在：" + userId);
        }
        if (orgTenantId == null || !orgTenantId.equals(user.getTenantId())) {
            throw AuthResultCode.PARAM_REFERENCE_MISSING.businessException(
                    "该用户与组织不属于同一租户（" + userId + "）—— "
                            + "判定两侧都限定租户，跨租户的组织归属永远不会生效");
        }
        return user;
    }

    /**
     * 生效区间必须是**非空**的（半开区间 {@code [from, to)}）。
     *
     * <p>起始晚于结束是一段**永远不会生效**的关系，而它在成员列表里看起来完全正常 ——
     * 与用户组、角色成员关系是同一条守卫。
     */
    private static void assertWindow(Long from, Long to) {
        if (from != null && to != null && from >= to) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "生效区间的起始时间必须早于结束时间，否则这段组织归属永远不会生效");
        }
    }

    /** 三态标志位只接受 0 / 1 —— 放行别的值不会报错，只会静默改变语义（判定读的就是它） */
    private static int flag(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value != 0 && value != 1) {
            throw AuthResultCode.PARAM_INVALID.businessException(
                    "isPrimaryPosition 只能是 0 或 1，收到：" + value);
        }
        return value;
    }

    /**
     * 写时间窗与岗位信息 —— **逐列显式 {@code set}，不用 {@code updateById}**。
     *
     * <p>字段策略不把 {@code null} 拼进 {@code SET}，于是"把临时借调改成长期有效"
     * （= 清掉时间窗）会**影响 0 行且不报错**。授权侧已因此付出过一次代价。
     */
    private void updateColumns(RelOrgUserEntity target, OrgMemberBindRequest request, Long from, Long to) {
        String positionName = FilterValue.text(request.getPositionName());
        String relationType = FilterValue.text(request.getRelationType());
        relOrgUserMapper.update(new RelOrgUserEntity(), Wrappers.<RelOrgUserEntity>lambdaUpdate()
                .eq(RelOrgUserEntity::getId, target.getId())
                .set(RelOrgUserEntity::getPositionName, positionName)
                .set(RelOrgUserEntity::getRelationType, relationType)
                .set(RelOrgUserEntity::getEffectiveFrom, from)
                .set(RelOrgUserEntity::getEffectiveTo, to));
        target.setPositionName(positionName);
        target.setRelationType(relationType);
        target.setEffectiveFrom(from);
        target.setEffectiveTo(to);
    }

    /** 把同键的其余行逻辑删除，只留下 {@code rows.get(0)} —— 见 {@link #bindMember} */
    private void revokeRedundantRows(List<RelOrgUserEntity> rows) {
        for (int i = 1; i < rows.size(); i += 1) {
            RelOrgUserEntity extra = rows.get(i);
            if (extra.getDeleted() == null || extra.getDeleted() == 0) {
                relOrgUserMapper.deleteById(extra.getId());
            }
        }
    }

    private static Set<Long> orgIdsOf(List<RelOrgUserEntity> rows) {
        return rows.stream().map(RelOrgUserEntity::getOrganizationId).collect(Collectors.toSet());
    }

    /**
     * 批量装配成员列表：成员名一次性查出来（一共一次查询、与成员数量无关），
     * 不逐行回查（N+1）。与 {@code UserGroupService.toMemberVOs} 同一条。
     */
    private List<OrgMemberVO> toMemberVOs(List<RelOrgUserEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, UserEntity> users = namesOf(rows.stream()
                .map(RelOrgUserEntity::getUserId)
                .collect(Collectors.toSet()));
        return rows.stream()
                .map(row -> toMemberVO(row, users.get(row.getUserId())))
                .toList();
    }

    private Map<Long, UserEntity> namesOf(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserEntity> users = new LinkedHashMap<>();
        for (UserEntity user : userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                .in(UserEntity::getId, ids))) {
            users.put(user.getId(), user);
        }
        return users;
    }

    private static OrgMemberVO toMemberVO(RelOrgUserEntity entity, UserEntity user) {
        OrgMemberVO vo = new OrgMemberVO();
        vo.setGrantId(entity.getId());
        vo.setMemberId(entity.getUserId());
        // 用户已被删除时 user 为 null：这里**刻意保留这一行**而不是过滤掉它 ——
        // 多态/无外键的关系表里这种行是可能存在的历史，而它正是
        // "为什么这个组织有 3 个人却只看到 2 个"的答案
        vo.setMemberName(user == null ? null : displayNameOf(user));
        vo.setIsPrimaryPosition(entity.getIsPrimaryPosition());
        vo.setPrimaryPosition(entity.getIsPrimaryPosition() != null && entity.getIsPrimaryPosition() == 1);
        vo.setPositionName(entity.getPositionName());
        vo.setRelationType(entity.getRelationType());
        vo.setEffectiveFrom(entity.getEffectiveFrom());
        vo.setEffectiveTo(entity.getEffectiveTo());
        vo.setEffectiveNow(isWithin(entity.getEffectiveFrom(), entity.getEffectiveTo(),
                System.currentTimeMillis()));
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private static UserOrgVO toUserOrgVO(RelOrgUserEntity entity, OrganizationEntity org) {
        UserOrgVO vo = new UserOrgVO();
        vo.setGrantId(entity.getId());
        vo.setOrgId(entity.getOrganizationId());
        vo.setOrgCode(org == null ? null : org.getOrgCode());
        vo.setOrgName(org == null ? null : org.getOrgName());
        vo.setFullName(org == null ? null : org.getFullName());
        vo.setOrgStatus(org == null ? null : org.getStatus());
        vo.setIsPrimaryPosition(entity.getIsPrimaryPosition());
        vo.setPrimaryPosition(entity.getIsPrimaryPosition() != null && entity.getIsPrimaryPosition() == 1);
        vo.setPositionName(entity.getPositionName());
        vo.setRelationType(entity.getRelationType());
        vo.setEffectiveFrom(entity.getEffectiveFrom());
        vo.setEffectiveTo(entity.getEffectiveTo());
        vo.setEffectiveNow(isWithin(entity.getEffectiveFrom(), entity.getEffectiveTo(),
                System.currentTimeMillis()));
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /** displayName 可空（建号时不强制填），回退到 username —— 界面拿到空名字比拿到用户名更麻烦 */
    private static String displayNameOf(UserEntity user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    /** 半开区间：[from, to)。两端都可空，都空表示长期有效 */
    private static Boolean isWithin(Long from, Long to, long now) {
        if (from != null && now < from) {
            return false;
        }
        return to == null || now < to;
    }

}
