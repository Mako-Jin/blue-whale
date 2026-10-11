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
 */
@Mapper
public interface AuthorizationMapper {

    /**
     * 查询用户**最终可用**的资源标识集合。
     *
     * <p>三条过滤规则，每条都对应一个真实事故：
     * <ul>
     *   <li>{@code effect <> 'DENY'} —— 显式拒绝优先于允许，取显式拒绝之外的资源；</li>
     *   <li>{@code is_enabled = 1} 与各表的 {@code deleted = 0} —— 停用的资源、
     *       已删除的角色不应继续产生权限，否则"停用"只是界面上的假象；</li>
     *   <li>{@code tenant_id} 同时参与角色与资源的限定 —— 防止跨租户的角色串用。</li>
     * </ul>
     *
     * <p><b>时态授权（effective_from / effective_to）本切片尚未纳入过滤</b>，
     * 表已留列，待权限判定补齐时一并加入。
     */
    @Select("""
            SELECT DISTINCT r.resource_code
            FROM bw_auth_resource r
            JOIN bw_auth_rel_role_resource rr ON rr.resource_id = r.id AND rr.deleted = 0
            JOIN bw_auth_role ro ON ro.id = rr.role_id AND ro.deleted = 0
            WHERE r.deleted = 0
              AND r.is_enabled = 1
              AND (rr.effect IS NULL OR rr.effect <> 'DENY')
              AND ro.tenant_id = #{tenantId}
              AND rr.role_id IN (
                  SELECT rm.role_id FROM bw_auth_rel_role_member rm
                  WHERE rm.deleted = 0
                    AND rm.tenant_id = #{tenantId}
                    AND (
                      (rm.member_type = 'USER' AND rm.member_id = #{userId})
                      OR (rm.member_type = 'GROUP' AND rm.member_id IN (
                            SELECT gm.user_group_id FROM bw_auth_rel_user_group_member gm
                            WHERE gm.deleted = 0
                              AND gm.tenant_id = #{tenantId}
                              AND gm.member_type = 'USER'
                              AND gm.member_id = #{userId}
                      ))
                    )
              )
            """)
    List<String> selectGrantedResourceCodes(@Param("tenantId") long tenantId,
                                            @Param("userId") long userId);

    /** 查询用户所拥有的角色编码（含经由用户组获得的） */
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
                    AND (
                      (rm.member_type = 'USER' AND rm.member_id = #{userId})
                      OR (rm.member_type = 'GROUP' AND rm.member_id IN (
                            SELECT gm.user_group_id FROM bw_auth_rel_user_group_member gm
                            WHERE gm.deleted = 0
                              AND gm.tenant_id = #{tenantId}
                              AND gm.member_type = 'USER'
                              AND gm.member_id = #{userId}
                      ))
                    )
              )
            """)
    List<String> selectRoleCodes(@Param("tenantId") long tenantId,
                                 @Param("userId") long userId);

}
