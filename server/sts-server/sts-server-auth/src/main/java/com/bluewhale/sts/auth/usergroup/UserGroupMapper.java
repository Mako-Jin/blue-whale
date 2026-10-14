package com.bluewhale.sts.auth.usergroup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户组仓储。
 *
 * <p>除继承来的通用 CRUD 外，本接口有两个**刻意绕过 {@code @TableLogic} 的例外**
 * （手写 SQL）：唯一索引 {@code uk_bw_auth_user_group_tenant_code} 建在
 * {@code (tenant_id, group_code)} 上、**不含 {@code deleted}**，
 * 而 MyBatis-Plus 给所有自动查询追加 {@code deleted = 0} ——
 * 用常规查询判重会看不见软删行，判重通过后 insert 撞唯一键，
 * 最终落到兜底的 {@code 40900}「数据已存在或与已有记录冲突」
 * （{@code ADR-0024} 之前是 500）。
 *
 * <p>正解是**复活那一行**，见 {@code UserGroupService.create}。
 * 同一个坑全仓共有**六处**，其中两处的答案是"拒绝"而不是复活 ——
 * 清单与判据见 {@code server/README.md} 的「唯一编码列与它们的软删语义」（ADR-0056）。
 * <b>不要在这里数"第几处"</b>：这个数字此前停在"第三处"，而实际有六处。
 *
 * <p>{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 */
@Mapper
public interface UserGroupMapper extends BaseMapper<UserGroupEntity> {

    /**
     * 按租户 + 组编码取一行，**包括已被逻辑删除的那些**。
     *
     * <p>限定 {@code tenant_id} 是必须的：编码只在租户内唯一，
     * 跨租户查到别人的同名组会让本租户无法使用这个编码。
     */
    @Select("""
            SELECT * FROM bw_auth_user_group
            WHERE tenant_id = #{tenantId} AND group_code = #{groupCode}
            """)
    UserGroupEntity selectAnyByTenantAndCode(@Param("tenantId") Long tenantId,
                                             @Param("groupCode") String groupCode);

    /**
     * 把一条已被逻辑删除的用户组恢复为正常。
     *
     * <p>{@code updateById} 的 WHERE 也带 {@code deleted = 0}，对软删行
     * **影响 0 行且不报错**，所以复活只能手写 SQL。
     *
     * <p>只在 {@code UserGroupService.create} 里、且已确认该编码确实被一条
     * 逻辑删除的组占着时调用 —— 这个方法的"越权"被调用点约束住。
     */
    @Update("UPDATE bw_auth_user_group SET deleted = 0 WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);

}
