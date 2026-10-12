package com.bluewhale.sts.auth.datascope;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authz.AuthorizationMapper;
import com.bluewhale.sts.auth.authz.RoleScopeView;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.enums.DataScope;
import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
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
 * <p><b>没有缓存到 {@link AuthContext} 上，是有意的。</b>权限集合在同一个请求里
 * 会被多处判定读（每个 {@code @RequiresPermission} 一次），所以必须请求级缓存；
 * 而数据范围目前只有"审计查询"一个消费方、一次请求读一次，加一层缓存
 * 只会引入"缓存何时失效"的新问题。
 *
 * <p><b>安全默认是"仅本人"，且由调用方兜住。</b>{@link #resolve} 在没有任何档位时
 * 返回 {@code scope == null}，而不是返回 {@code ALL} —— 一个没有配数据范围的人
 * 不能因此看见全租户。所有调用点都必须以"不是 ALL 就收窄到本人"的方式使用它。
 */
@Service
public class DataScopeService {

    private final AuthorizationMapper authorizationMapper;
    private final UserMapper userMapper;

    public DataScopeService(AuthorizationMapper authorizationMapper, UserMapper userMapper) {
        this.authorizationMapper = authorizationMapper;
        this.userMapper = userMapper;
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
        return merge(loadEffectiveRoles(tenantId, userId));
    }

    /** 求当前登录用户的数据范围。供"一定在认证之后执行"的查询使用 */
    @Transactional(readOnly = true)
    public UserDataScopeVO resolveForCurrent() {
        AuthContext context = AuthContextHolder.require();
        return merge(loadEffectiveRoles(context.getTenantId(), context.getUserId()));
    }

    /**
     * 当前请求是否应当被收窄到"只看自己"。
     *
     * <p>这是给查询侧用的**单一入口**，语义刻意写成"是不是只看自己"而不是
     * "范围是什么"：调用方只需要知道"要不要加一个 {@code operator_id = 我} 的条件"，
     * 让它自己去解释五个档位，等于把合并规则复制到每个查询里。
     *
     * <p><b>只有 ALL 才会返回 false。</b>取非 ALL 的全部档位（含 DEPT 系列与 CUSTOM）
     * 都返回 true —— 因为它们算不出精确范围，而"算不出"必须往**窄**的方向兜底。
     * 反过来（算不出就当 ALL）会让"配了一个还没实现的范围"变成一次静默的越权。
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
     */
    private static UserDataScopeVO merge(List<RoleScopeView> roles) {
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
        vo.setPrecise(widest.precise());
        if (!widest.precise()) {
            vo.setNote(widest.getLabel()
                    + " 尚不能被精确求值（需要组织归属或 filter_json 解析），"
                    + "已接入的查询保守地按「仅本人」处理");
        }
        return vo;
    }

}
