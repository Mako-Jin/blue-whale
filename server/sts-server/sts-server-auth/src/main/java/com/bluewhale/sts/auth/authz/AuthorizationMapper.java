package com.bluewhale.sts.auth.authz;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 授权关系查询 —— RBAC 判定的取数入口。
 *
 * <p>本 Mapper 只做**读**，且只做"这个人最终有哪些权限"这一件事。
 * 授权关系的写（给角色授资源、给用户授角色）由各关系仓储负责，
 * 职责分开：判定是高频热路径，写入是低频管理动作，两者的优化目标不同。
 *
 * <p><b>为什么用 JOIN 写 SQL 而不在 Java 里拼多次查询：</b>
 * "用户的权限"要穿过 用户 → (角色成员) → 角色 → (角色资源) → 资源 四张表。
 * 在 Java 里分步查会产生 3~4 次往返，且中间结果要来回传；
 * 一条 SQL 交给数据库用索引完成，是这个场景下更合适的做法。
 *
 * <p><b>多态成员的处理</b>：角色可以授予用户，也可以授予用户组。
 * 这里把两种来源并成一条 {@code IN} 子查询 —— 用户直接拥有的角色，
 * 与"用户所属用户组所拥有的角色"取并集。
 *
 * <p><b>但子查询只跳一层，嵌套组（组里有组）在判定里不生效。</b>
 * {@code bw_auth_rel_user_group_member} 与 {@link com.bluewhale.sts.auth.common.enums.MemberType}
 * 的注释都写着"组可以嵌套"，表的结构也确实支持（{@code member_type} 同样是多态列），
 * 但这两条 SQL 都把 {@code gm.member_type} 钉成了 {@code 'USER'} ——
 * 也就是只认"组 → 用户"这一层。把"上海分部"放进"华东大区"、再把角色授给"华东大区"，
 * 上海分部的成员**拿不到权限**，而成员列表里一切正常。
 *
 * <p>刻意不在本切片补上：真要做需要 {@code WITH RECURSIVE}，而它在三套方言上的写法
 * 与版本要求都不一致（达梦在 Oracle 兼容模式下的支持程度未验证过），
 * 仓促加进去的风险是把"嵌套不生效"换成"某套库上整条判定失败" ——
 * 后者严重得多，且不会在 h2 上暴露。触发条件：真的需要嵌套组时，
 * 且先在达梦实测 {@code WITH RECURSIVE}。在那之前，嵌套组这条路应当由
 * 用户组接口显式拒绝，而不是默默接受。
 *
 * <p><b>时间是一个入参，而不是 {@code NOW()}。</b>判定要用"这一次请求的时刻"，
 * 而 {@code NOW()} 由数据库评估：同一请求里判定两次（多处 {@code @RequiresPermission}）
 * 会各自取一次当前时间，跨越授权到期边界的请求因此可能"第一次有权限、第二次没有"。
 * 传进来则由调用方决定 —— {@code PermissionChecker} 是请求级缓存，
 * 同一请求内天然一致。附带好处是三套数据库不必各自解释时间函数。
 */
@Mapper
public interface AuthorizationMapper {

    /**
     * 查询用户**最终可用**的资源标识（即权限码）集合。
     *
     * <p>五条过滤规则，每条都对应一个真实事故：
     * <ul>
     *   <li>{@code r.is_enabled = 1} 与各表的 {@code deleted = 0} —— 停用的资源、
     *       已删除的角色/资源不应继续产生权限，否则"停用/删除"只是界面上的假象；</li>
     *   <li>{@code ro.status} —— <b>停用的角色同样不应产生权限</b>。这一条与上一条是
     *       同一个道理，但曾经漏掉：角色管理接口落地后"停用角色"成为一个可点的操作，
     *       漏掉它就意味着点完之后权限照旧。</li>
     *   <li>{@code ro.tenant_id} 参与角色限定 —— 防止跨租户的角色串用
     *       （资源侧刻意不限定，资源是平台级的，见 {@code ResourceService}）；</li>
     *   <li>{@code effective_from / effective_to} —— 时态授权：只认当前时刻落在区间内的记录。
     *       "到期自动失效"因此不需要定时任务去删数据，而定时任务一旦没跑，
     *       权限就会静默地多留一天；时态过滤不会。</li>
     * </ul>
     *
     * <p><b>DENY 的语义：禁用一条允许，而不是删掉一条允许。</b>
     * 这是本 SQL 里最容易被写错的地方，也是它从前写错过的地方：
     * 早先的写法在 JOIN 上过滤 {@code effect <> 'DENY'}，看起来"排除了拒绝"，
     * 实际排掉的是**那一行**。于是"角色 A 允许 + 角色 B 拒绝"时，A 那一行照样通过，
     * 资源仍然是可用的 —— 也就是说 {@code GrantEffect} 承诺的"命中任意一条 DENY 即拒绝"
     * 只在**同一个角色内**成立，跨角色完全失效。而跨角色恰恰是 DENY 唯一的用途：
     * 同一角色的同一个资源上放两条相反的规则毫无意义。
     *
     * <p>改法是以资源为单位做聚合，再用 {@code HAVING} 表达"这个资源上一条 DENY 都不能有"：
     * <pre>
     *   分组：GROUP BY r.resource_code
     *   拒绝：HAVING SUM(DENY 的行数) = 0
     * </pre>
     * 行数而非布尔值，是因为用户可能同时持有多个角色，"有没有 DENY"要跨这些角色一起看。
     * 注意分组之后**不再需要 DISTINCT**：同一个资源只会产出一行。
     *
     * <p>顺带说明为什么这样做不需要唯一的 (role_id, resource_id) 约束也安全：
     * 重复的 ALLOW 行只是让 SUM 变大，重复的 ALLOW+DENY 由 DENY 胜出 ——
     * 聚合把"到底有几行"这件事的语义影响消掉了。
     */
    @Select("""
            SELECT r.resource_code
            FROM bw_auth_resource r
            JOIN bw_auth_rel_role_resource rr ON rr.resource_id = r.id AND rr.deleted = 0
            JOIN bw_auth_role ro ON ro.id = rr.role_id AND ro.deleted = 0
            WHERE r.deleted = 0
              AND r.is_enabled = 1
              AND ro.tenant_id = #{tenantId}
              AND (ro.status IS NULL OR ro.status = 'ENABLED')
              AND (rr.effective_from IS NULL OR rr.effective_from <= #{now})
              AND (rr.effective_to IS NULL OR rr.effective_to > #{now})
              AND rr.role_id IN (
                  SELECT rm.role_id FROM bw_auth_rel_role_member rm
                  WHERE rm.deleted = 0
                    AND rm.tenant_id = #{tenantId}
                    AND (rm.effective_from IS NULL OR rm.effective_from <= #{now})
                    AND (rm.effective_to IS NULL OR rm.effective_to > #{now})
                    AND (
                      (rm.member_type = 'USER' AND rm.member_id = #{userId})
                      OR (rm.member_type = 'GROUP' AND rm.member_id IN (
                            SELECT gm.user_group_id FROM bw_auth_rel_user_group_member gm
                            WHERE gm.deleted = 0
                              AND gm.tenant_id = #{tenantId}
                              AND gm.member_type = 'USER'
                              AND gm.member_id = #{userId}
                              AND (gm.effective_from IS NULL OR gm.effective_from <= #{now})
                              AND (gm.effective_to IS NULL OR gm.effective_to > #{now})
                      ))
                    )
              )
            GROUP BY r.resource_code
            HAVING SUM(CASE WHEN rr.effect = 'DENY' THEN 1 ELSE 0 END) = 0
            """)
    List<String> selectGrantedResourceCodes(@Param("tenantId") long tenantId,
                                            @Param("userId") long userId,
                                            @Param("now") long now);

    /**
     * 查询用户所拥有的角色编码（含经由用户组获得的）。
     *
     * <p>与上一条保持同一套开关：角色要启用、关系要未删、时态要落在区间内。
     * 两处若不一致，会出现"角色列表里看得到、权限却没有"或反过来的怪状态，
     * 而这两种表现都会被当成前端 bug 排查。
     */
    @Select("""
            SELECT DISTINCT ro.role_code
            FROM bw_auth_role ro
            WHERE ro.deleted = 0
              AND ro.tenant_id = #{tenantId}
              AND (ro.status IS NULL OR ro.status = 'ENABLED')
              AND ro.id IN (
                  SELECT rm.role_id FROM bw_auth_rel_role_member rm
                  WHERE rm.deleted = 0
                    AND rm.tenant_id = #{tenantId}
                    AND (rm.effective_from IS NULL OR rm.effective_from <= #{now})
                    AND (rm.effective_to IS NULL OR rm.effective_to > #{now})
                    AND (
                      (rm.member_type = 'USER' AND rm.member_id = #{userId})
                      OR (rm.member_type = 'GROUP' AND rm.member_id IN (
                            SELECT gm.user_group_id FROM bw_auth_rel_user_group_member gm
                            WHERE gm.deleted = 0
                              AND gm.tenant_id = #{tenantId}
                              AND gm.member_type = 'USER'
                              AND gm.member_id = #{userId}
                              AND (gm.effective_from IS NULL OR gm.effective_from <= #{now})
                              AND (gm.effective_to IS NULL OR gm.effective_to > #{now})
                      ))
                    )
              )
            """)
    List<String> selectRoleCodes(@Param("tenantId") long tenantId,
                                 @Param("userId") long userId,
                                 @Param("now") long now);

}
