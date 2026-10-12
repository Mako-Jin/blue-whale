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
 * （手写 SQL）与一个角色授权视图的读方法。三处都不是"顺手多写的 SQL"，
 * 理由分别写在下面。
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

}
