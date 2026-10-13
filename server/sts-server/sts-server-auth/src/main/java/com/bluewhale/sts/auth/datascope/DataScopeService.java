package com.bluewhale.sts.auth.datascope;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authz.AuthorizationMapper;
import com.bluewhale.sts.auth.authz.RoleScopeView;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.DataScope;
import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.organization.OrganizationEntity;
import com.bluewhale.sts.auth.organization.OrganizationMemberService;
import com.bluewhale.sts.auth.organization.OrganizationService;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 数据范围求值 —— 把"这个人身上所有角色的 {@code data_scope}"合成一个结果。
 *
 * <p><b>它的位置在"配置侧"与"查询侧"之间。</b>配置侧写的是每个角色的档位，
 * 查询侧要问的是"这一次请求该看到多大范围"。这个类负责把前者翻译成后者，
 * 于是所有要按数据范围过滤的查询都从这里取答案，而不是各自去读角色表 ——
 * 各自读的结果一定是各自实现一遍合并规则，然后各自实现得不一致。
 *
 * <p><b>复用判定链，不自己再写一套生效条件。</b>取有效角色走的是
 * {@link AuthorizationMapper#selectEffectiveRoles}，与"有哪些权限码"
 * 是同一组角色、同一套过滤（角色未删未停用、成员关系未删、两层时态都在区间内）。
 * 若数据范围用另一套"什么算生效"的定义，就会出现"有权限但范围变了"
 * 这种无法解释的组合，而两边各自的测试都会通过。
 *
 * <p><b>组织归属落地之后，{@code DEPT} 系列第一次可以被求值</b>（见 {@code ADR-0030}）。
 * 求值入口是 {@link OrganizationMemberService#primaryOrganizationOf}：主岗所在、
 * 时态内、且**未停用**的那个组织。求到了就把范围翻译成一组组织 id 下发；
 * 求不到（没归属 / 已过期 / 部门被停用）就**往窄兜底**，仍然当作"仅本人"。
 *
 * <p><b>「没有缓存到 {@link AuthContext} 上」仍然是有意的。</b>权限集合在同一个请求里
 * 会被多处判定读（每个 {@code @RequiresPermission} 一次），所以必须请求级缓存；
 * 而数据范围目前只有"审计查询"与"反查接口"两个消费方、一次请求各读一次，
 * 加一层缓存只会引入"缓存何时失效"的新问题 —— 而组织归属是**会因为
 * 一次成员写入而立刻变化**的东西，缓存它需要先回答"谁负责失效"。
 *
 * <p><b>安全默认是"仅本人"，且由调用方兜住。</b>{@link #resolve} 在没有任何档位时
 * 返回 {@code scope == null}，而不是返回 {@code ALL} —— 一个没有配数据范围的人
 * 不能因此看见全租户。所有调用点都必须以"不是 ALL 就收窄到本人"的方式使用它。
 */
@Service
public class DataScopeService {

    private final AuthorizationMapper authorizationMapper;
    private final UserMapper userMapper;
    private final OrganizationMemberService organizationMemberService;
    private final OrganizationService organizationService;

    public DataScopeService(AuthorizationMapper authorizationMapper,
                            UserMapper userMapper,
                            OrganizationMemberService organizationMemberService,
                            OrganizationService organizationService) {
        this.authorizationMapper = authorizationMapper;
        this.userMapper = userMapper;
        this.organizationMemberService = organizationMemberService;
        this.organizationService = organizationService;
    }

    /**
     * 求指定用户的数据范围。用于反查接口 —— 因此要校验该用户**在本租户内**存在，
     * 否则"查一个不存在的用户"会返回一个看起来正常的空结果（很像"这个人没有任何范围"），
     * 跨租户查别人的用户同样会被这里挡住。
     */
    @Transactional(readOnly = true)
    public UserDataScopeVO resolve(long userId) {
        long tenantId = AuthContextHolder.require().getTenantId();
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getId, userId)
                .eq(UserEntity::getTenantId, tenantId));
        if (user == null) {
            throw AuthResultCode.USER_NOT_FOUND.businessException("用户不存在或不属于当前租户");
        }
        return merge(loadEffectiveRoles(tenantId, userId), tenantId, userId);
    }

    /** 求当前登录用户的数据范围。供"一定在认证之后执行"的查询使用 */
    @Transactional(readOnly = true)
    public UserDataScopeVO resolveForCurrent() {
        AuthContext context = AuthContextHolder.require();
        return merge(loadEffectiveRoles(context.getTenantId(), context.getUserId()),
                context.getTenantId(), context.getUserId());
    }

    /**
     * 当前请求是否应当被收窄到"只看自己"。
     *
     * <p>这是给查询侧用的**单一入口**，语义刻意写成"是不是只看自己"而不是
     * "范围是什么"：调用方只需要知道"要不要加一个 {@code operator_id = 我} 的条件"，
     * 让它自己去解释五个档位，等于把合并规则复制到每个查询里。
     *
     * <p><b>只有 ALL 才会返回 false，而这条规则在组织归属落地之后**没有改变** ——
     * 理由变了，结论没变，这一点必须写清楚，否则下一个人会以为它是过期的。</b>
     * 旧理由是"DEPT 系列算不出精确范围"（那时确实算不出）；现在算得出了，
     * 但把它接进来的效果是**放宽**：
     * <ul>
     *   <li>当前唯一的消费方是审计查询，而 {@code bw_audit_log} **没有组织维度** ——
     *       要按部门过滤只能退化成 {@code operator_id IN (该部门的用户)}；</li>
     *   <li>那意味着一次**静默的权限放宽**：升级之后，一个配了 {@code DEPT} 的角色
     *       会突然能看到整个部门的审计记录，而它的配置一个字都没改。
     *       审计是合规追责用的数据，这种"升级即扩权"是本仓最不接受的效果之一
     *       （与 {@code 0025} 的"算不出往窄兜底"、{@code 0029} 的"有默认值就等于
     *       一条静默的误配路径"是同一条判断）；</li>
     *   <li>而当前也没有人要求这个能力。等真有需求时，它应当带着自己的 ADR 与
     *       自己的测试落地 —— 那是一次**显式的**扩大可见范围，需要能被单独评审。</li>
     * </ul>
     * 兜底方向仍然是"往窄"：配了 DEPT 但主岗算不出（没有主岗、已过期、部门停用）时，
     * 这里同样返回 true。
     */
    @Transactional(readOnly = true)
    public boolean currentRequestSelfOnly() {
        return !resolveForCurrent().isAllData();
    }

    private List<RoleScopeView> loadEffectiveRoles(long tenantId, long userId) {
        return authorizationMapper.selectEffectiveRoles(tenantId, userId, System.currentTimeMillis());
    }

    /**
     * 合并规则：取最宽的一档。见 {@link DataScope} 的 {@code rank} 说明。
     *
     * <p>组织归属**只在这一步解析一次**，且只在最宽档位确实需要它时才解析 ——
     * 最宽是 ALL 或 SELF 时（自足）多查一次组织表既是浪费，也会让
     * "这个人的组织归属有问题"意外影响到一次本来完全不需要组织的求值。
     */
    private UserDataScopeVO merge(List<RoleScopeView> roles, long tenantId, long userId) {
        List<String> roleCodes = roles.stream()
                .map(RoleScopeView::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<DataScope> held = roles.stream()
                .map(RoleScopeView::getDataScope)
                .filter(Objects::nonNull)
                .map(DataScope::parse)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingInt(DataScope::rank).reversed())
                .toList();

        UserDataScopeVO vo = new UserDataScopeVO();
        vo.setRoleCodes(roleCodes);
        vo.setScopes(held.stream().map(DataScope::wireName).toList());

        DataScope widest = held.isEmpty() ? null : held.get(0);
        if (widest == null) {
            vo.setAllData(false);
            vo.setPrecise(true);
            vo.setNote("没有任何生效角色携带数据范围，按「仅本人」处理");
            return vo;
        }

        vo.setScope(widest.wireName());
        vo.setScopeLabel(widest.getLabel());
        vo.setAllData(widest == DataScope.ALL);

        if (widest.selfSufficient()) {
            // ALL 与 SELF 不需要任何别的数据就能落到过滤条件上
            vo.setPrecise(true);
            return vo;
        }
        if (widest.needsOrgContext()) {
            applyOrgScope(vo, widest, tenantId, userId);
            return vo;
        }

        // CUSTOM：既不自足，也不是组织问题 —— filter_json 的解析本仓仍未实现
        vo.setPrecise(false);
        vo.setNote(widest.getLabel()
                + " 尚不能被精确求值（需要解析角色的 filter_json，本仓未实现），"
                + "已接入的查询保守地按「仅本人」处理");
        return vo;
    }

    /**
     * 把 {@code DEPT} / {@code DEPT_AND_CHILD} 翻译成一组组织 id。
     *
     * <p>两种情况都必须能区分开，因为它们的**处置相同、原因不同**：
     * 求不到主岗时 precise=false，求到时 precise=true；
     * 而后者还要再补一句"目前没有查询按组织过滤"—— 不写这句的话，
     * 一个看到"本部门及下级（3 个组织）"的管理员会合理地以为数据已经被过滤了。
     * 本仓对"看起来生效、实际不生效"的东西一向要求显式说出来。
     */
    private void applyOrgScope(UserDataScopeVO vo, DataScope widest, long tenantId, long userId) {
        OrganizationEntity primaryOrg = organizationMemberService.primaryOrganizationOf(userId, tenantId);
        if (primaryOrg == null) {
            vo.setPrecise(false);
            vo.setNote(widest.getLabel()
                    + " 需要组织归属，但该用户当前**没有可用的主岗**"
                    + "（没有组织归属、归属已过期、或主岗所在部门已停用），"
                    + "已接入的查询保守地按「仅本人」处理");
            return;
        }

        vo.setPrecise(true);
        vo.setPrimaryOrgId(primaryOrg.getId());
        vo.setPrimaryOrgName(primaryOrg.getOrgName());
        vo.setPrimaryOrgFullName(primaryOrg.getFullName());
        vo.setVisibleOrgIds(widest == DataScope.DEPT
                ? List.of(primaryOrg.getId())
                : organizationService.enabledSubtreeIds(primaryOrg));
        vo.setNote("范围已可精确求值，但**目前没有任何查询按它过滤**："
                + "本仓尚无业务数据表，而审计域刻意保持「非 ALL 即仅本人」"
                + "（把它接进审计会是一次静默的权限放宽）。"
                + "这里的组织集合是求值口径的如实呈现，不是「已生效的过滤」");
    }

}
