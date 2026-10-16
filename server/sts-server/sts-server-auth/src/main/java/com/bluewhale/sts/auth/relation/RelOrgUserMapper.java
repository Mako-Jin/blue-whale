package com.bluewhale.sts.auth.relation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 组织-用户仓储。
 *
 * <p><b>本表与前三处成员关系表有一处关键差别：它连唯一索引都没有。</b>
 * 建表脚本给 {@code bw_auth_rel_org_user} 只建了两个普通索引
 * （{@code organization_id}、{@code user_id}），没有
 * {@code (organization_id, user_id)} 的唯一键 —— 也就是说数据库层面
 * **允许同一个人在同一组织下有两条有效归属**。
 *
 * <p>连带的后果有两个，都必须由服务层兜住：
 * <ul>
 *   <li><b>重复增加不会撞键</b>，所以 {@code OrganizationMemberService.bindMember}
 *       必须按 (组织, 用户) 先查后写（"存在就改、软删了就复活"），
 *       并把同键的其余行清理掉 —— 否则一次并发调用就能留下两条行，
 *       界面上的成员列表会出现同一个人两次；</li>
 *   <li><b>主岗唯一完全靠服务层保证</b>。"每个用户在本租户内至多一个主岗"
 *       没有数据库约束兜底，因此设主岗的那一步必须先给别处的同岗降级。
 *       详见 {@code OrganizationMemberService.applyPrimaryPosition}。</li>
 * </ul>
 * 没有加唯一索引是**刻意的**，不是遗漏：{@code 0009} 定下"已执行脚本不可改"，
 * 而补一个唯一索引要先清理历史重复行，那属于独立的迁移。
 * 记在这里，是为了让"为什么这里要先查后写"有一个明确答案。
 */
@Mapper
public interface RelOrgUserMapper extends BaseMapper<RelOrgUserEntity> {

    /**
     * 按 (组织, 用户) 取全部行，**包括已被逻辑删除的那些**。
     *
     * <p>绕过 {@code @TableLogic}（手写 SQL）是必须的：常规查询只看得见
     * {@code deleted = 0} 的行，于是"删掉归属、再加回来"会走成 insert，
     * 而 insert 不撞任何唯一键 —— 结果是同一个 (组织, 用户) 在表里积累出多行，
     * 其中一行是活的、其余是历史。历史行本身没错，错的是**不知道存在它们**：
     * 复活时应当复活原来那一行，这样 {@code create_time}（"这段归属从什么时候开始"）
     * 才留得住。
     *
     * <p>按 {@code id} 升序返回：调用方约定取第一条作为"目标行"、
     * 其余同键行逻辑删除，顺序稳定才能让这个约定可预测。
     */
    @Select("""
            SELECT * FROM bw_auth_rel_org_user
            WHERE organization_id = #{organizationId} AND user_id = #{userId}
            ORDER BY id
            """)
    List<RelOrgUserEntity> selectAnyByOrgAndUser(@Param("organizationId") Long organizationId,
                                                 @Param("userId") Long userId);

    /**
     * 把一条已被逻辑删除的组织归属恢复为正常。
     *
     * <p>{@code updateById} 的 WHERE 也带 {@code deleted = 0}，对软删行
     * **影响 0 行且不报错**，所以复活只能手写 SQL。
     *
     * <p><b>租户条件是这条 SQL 自己的事</b>（{@code decisions/0079}）：它绕过
     * {@code @TableLogic}，也就绕过了"先 {@code requireXxx} 再改"那条链上唯一的租户保证。
     * 改前这一格只靠调用点（按"组织 + 用户"查出来的行，其租户已被上游校验过）——
     * 而那条论证不在 SQL 里，也不在编译期。形状由规则 F 守。
     */
    @Update("UPDATE bw_auth_rel_org_user SET deleted = 0 WHERE id = #{id} AND tenant_id = #{tenantId}")
    int restoreDeletedById(@Param("id") Long id, @Param("tenantId") Long tenantId);

}
