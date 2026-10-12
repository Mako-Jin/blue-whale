package com.bluewhale.sts.auth.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 角色仓储。
 *
 * <p>除继承来的通用 CRUD 外，本接口有两个**刻意绕过 {@code @TableLogic} 的例外**
 * （手写 SQL）与两个跨表视图的读方法。四个都不是"顺手多写的 SQL"，
 * 理由分别写在下面。
 *
 * <p>两个视图（角色的授权集合 / 某人的角色来源）都放在本仓储、返回类型也定义在
 * 角色包内，是为了让 {@code relation} 包不必反向依赖 {@code role} 包 ——
 * 这两个视图的返回类型都不是实体，放错地方依赖方向就反了。
 *
 * <p>{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /**
     * 按租户 + 角色编码取一行，**包括已被逻辑删除的那些**。
     *
     * <p><b>为什么必须绕过 {@code @TableLogic}：</b>唯一索引
     * {@code uk_bw_auth_role_tenant_code} 建在 {@code (tenant_id, role_code)} 上，
     * **不含 {@code deleted}** —— 也就是说被逻辑删除的行仍然占着这个编码。
     * 而 MyBatis-Plus 给每条自动生成的查询都追加 {@code deleted = 0}，
     * 于是用常规查询去判重时会**看不见那些已删除的行**：判重通过 → 走到 insert →
     * 撞唯一键 → 抛 {@code DuplicateKeyException} → 被兜底成 500。
     * 用户看到"系统内部错误"，真实原因只是"这个编码你删过"。
     *
     * <p>这个坑与资源目录是**同一个**（{@code ResourceMapper.selectAnyByCode}），
     * 只是索引从单列变成了复合列，症状一模一样。所以这里也走同一条正解：先"看见"，
     * 再决定是复活还是拒绝 —— 见 {@code RoleService.create}。
     *
     * <p>限定 {@code tenant_id} 是必须的：编码只在租户内唯一，
     * 跨租户查到别人的同名角色会让本租户无法使用这个编码。
     */
    @Select("""
            SELECT * FROM bw_auth_role
            WHERE tenant_id = #{tenantId} AND role_code = #{roleCode}
            """)
    RoleEntity selectAnyByTenantAndCode(@Param("tenantId") Long tenantId,
                                       @Param("roleCode") String roleCode);

    /**
     * 把一条已被逻辑删除的角色恢复为正常。
     *
     * <p>与 {@code ResourceMapper.restoreDeletedById} 同理：{@code updateById}
     * 的 WHERE 也会带上 {@code deleted = 0}，对已删除的行**影响 0 行且不报错**，
     * 所以复活只能用手写 SQL。
     *
     * <p>这个方法的代价（绕过逻辑删除保护）在调用点上被约束住：只在
     * {@code RoleService.create} 里、且已确认该编码确实被一条逻辑删除的角色占着时调用。
     */
    @Update("UPDATE bw_auth_role SET deleted = 0 WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);

    /**
     * 列出某角色已授予的资源（带资源侧的信息），供授权管理界面展示。
     *
     * <p><b>为什么这条 JOIN 放在角色仓储、而不是关系仓储：</b>它是"角色这个聚合
     * 的授权集合"这个视图 —— 调用方拿到的是角色侧的自洽快照，一次查询取完，
     * 不必先取关系行再逐条回查资源（N+1）。把返回类型 {@link RoleResourceVO}
     * 定义在角色包内，也让 {@code relation} 包不必反向依赖 {@code role} 包
     * （若把这条查询放进 {@code RelRoleResourceMapper}，依赖方向就反了）。
     *
     * <p>资源侧要求 {@code r.deleted = 0}：指向已被删除资源的授权行没有意义。
     * 实际上这种行不该存在 —— {@code ResourceService.delete} 在资源被引用时就会拒绝，
     * 所以这里是防御性的，不是主路径。
     *
     * <p>列名显式起别名，不依赖下划线转驼峰的推导：这条查询的返回类型不是实体，
     * 一旦某天推导规则变了，静默失效的形式是"字段全是 null"，很难发现。
     */
    @Select("""
            SELECT rr.id             AS grant_id,
                   rr.resource_id    AS resource_id,
                   r.resource_code   AS resource_code,
                   r.resource_name   AS resource_name,
                   r.resource_type   AS resource_type,
                   r.is_enabled      AS is_enabled,
                   rr.effect         AS effect,
                   rr.priority       AS priority,
                   rr.effective_from AS effective_from,
                   rr.effective_to   AS effective_to,
                   rr.create_time    AS create_time
            FROM bw_auth_rel_role_resource rr
            JOIN bw_auth_resource r ON r.id = rr.resource_id AND r.deleted = 0
            WHERE rr.deleted = 0 AND rr.role_id = #{roleId}
            ORDER BY r.sort_no, r.id
            """)
    List<RoleResourceVO> selectResourceGrants(@Param("roleId") Long roleId);

    /**
     * 反查某个人身上挂了哪些角色、每条是**从哪来的**（直接授予 / 经用户组授予）。
     *
     * <p><b>这一行 SQL 与 `AuthorizationMapper` 的那两条长得很像，但不是同一件事，
     * 别顺手合并：</b>判定要的是"此刻有效的权限码集合"（聚合、过滤、去重），
     * 反查要的是"配置里有哪些路径"（不过滤、逐条、带来源）。把两者写成一条，
     * 只会得到一条既看不懂也改不动的 SQL —— 而它们的**演进方向正好相反**：
     * 判定会越来越严（多一个开关就多一个 WHERE），反查会越来越宽（多一个来源就多一个分支）。
     *
     * <p><b>不过滤时态与状态，这是刻意的。</b>如果在这里按判定条件筛一遍，
     * 反查就会退化成 {@code /api/auth/me} 的副本，"我明明授了权、怎么没有"依旧没人能回答。
     * 所以时间窗与状态原样返回，由 {@code RoleMemberService.rolesOfUser} 补上派生标记。
     *
     * <p><b>用户组的两种挂载方式在这里被合并成一列 {@code source}：</b>
     * {@code member_type} 是多态表内部的实现细节（{@code USER} / {@code GROUP}），
     * 而调用方要的是"这条角色是人直接拿的，还是通过某个组拿的"。
     * 在 SQL 里用 {@code CASE} 翻译掉，比让 Java 侧记住
     * "member_id 在 GROUP 时才表示组"要可靠 —— 后者正是那个会撞号的语义。
     *
     * <p><b>{@code LEFT JOIN} 的 ON 里带 {@code rm.member_type = 'GROUP'}：</b>
     * 它让"组成员"那张表只对组类型的关系行参与连接，用户直授的行自然得到一个 NULL。
     * 写在 ON 而不是 WHERE，是因为 WHERE 会把用户直授的行整行滤掉 ——
     * 那是内连接的语义，正是这里不想要的。
     *
     * <p>{@code ro.deleted = 0} 与 {@code ro.tenant_id} 是必须的：前者排除已删角色，
     * 后者防止跨租户的角色串进列表（角色必然属于某个租户，见 {@code RoleQuery}）。
     */
    @Select("""
            SELECT rm.id             AS grant_id,
                   rm.effective_from AS effective_from,
                   rm.effective_to   AS effective_to,
                   rm.create_time    AS create_time,
                   CASE WHEN rm.member_type = 'GROUP' THEN 'GROUP' ELSE 'DIRECT' END AS source,
                   CASE WHEN rm.member_type = 'GROUP' THEN rm.member_id END         AS group_id,
                   ro.id             AS role_id,
                   ro.role_code      AS role_code,
                   ro.role_name      AS role_name,
                   ro.status         AS role_status,
                   gm.effective_from AS group_member_effective_from,
                   gm.effective_to   AS group_member_effective_to
            FROM bw_auth_rel_role_member rm
            JOIN bw_auth_role ro ON ro.id = rm.role_id AND ro.deleted = 0
            LEFT JOIN bw_auth_rel_user_group_member gm
                   ON rm.member_type = 'GROUP'
                  AND gm.user_group_id = rm.member_id
                  AND gm.deleted = 0
                  AND gm.tenant_id = #{tenantId}
                  AND gm.member_type = 'USER'
                  AND gm.member_id = #{userId}
            WHERE rm.deleted = 0
              AND rm.tenant_id = #{tenantId}
              AND ro.tenant_id = #{tenantId}
              AND (
                    (rm.member_type = 'USER' AND rm.member_id = #{userId})
                 OR (rm.member_type = 'GROUP' AND gm.id IS NOT NULL)
              )
            ORDER BY ro.role_code, rm.member_type, rm.id
            """)
    List<UserRoleVO> selectUserRoleSources(@Param("tenantId") Long tenantId,
                                          @Param("userId") Long userId);

}
