package com.bluewhale.sts.auth.organization;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 组织仓储。
 *
 * <p>除继承来的通用 CRUD 外，本接口有两个**刻意绕过 {@code @TableLogic} 的例外**
 * （手写 SQL），是同一个坑在**第四处**的复现（前三处：资源、角色、用户组）：
 * 唯一索引 {@code uk_bw_auth_org_tenant_code} 建在 {@code (tenant_id, org_code)} 上、
 * **不含 {@code deleted}**，而 MyBatis-Plus 给所有自动查询追加 {@code deleted = 0} ——
 * 用常规查询判重看不见软删行，判重通过后 insert 撞唯一键，
 * 最终被兜底成"系统内部错误"。于是"删掉一个部门、想用同一个编码再建一个"
 * 这个再正常不过的操作会失败。
 *
 * <p>正解同样是**复活那一行**，见 {@code OrganizationService.create}。
 *
 * <p><b>子树重写没有做成一条 SQL，是刻意的。</b>{@code code_path} 的前缀替换在
 * MySQL / H2 / 达梦三方言里分别要用 {@code SUBSTRING} / {@code CONCAT} /
 * {@code CHAR_LENGTH} 且语义有细微差别（达梦还要考虑 {@code VARCHAR(n CHAR)}），
 * 而组织树的规模是人手维护的量级（几十到几百个节点）——
 * 用逐行 update 换掉一次跨方言的字符串手术，这笔交易是划算的。
 * 详见 {@code OrganizationService.rewriteSubtree}。
 *
 * <p>{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 */
@Mapper
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {

    /**
     * 按租户 + 组织编码取一行，**包括已被逻辑删除的那些**。
     *
     * <p>限定 {@code tenant_id} 是必须的：编码只在租户内唯一，
     * 跨租户查到别人的同名组织会让本租户无法使用这个编码。
     */
    @Select("""
            SELECT * FROM bw_auth_organization
            WHERE tenant_id = #{tenantId} AND org_code = #{orgCode}
            """)
    OrganizationEntity selectAnyByTenantAndCode(@Param("tenantId") Long tenantId,
                                                @Param("orgCode") String orgCode);

    /**
     * 把一条已被逻辑删除的组织恢复为正常。
     *
     * <p>{@code updateById} 的 WHERE 也带 {@code deleted = 0}，对软删行
     * **影响 0 行且不报错**，所以复活只能手写 SQL。
     *
     * <p>只在 {@code OrganizationService.create} 里、且已确认该编码确实被一条
     * 逻辑删除的组织占着时调用 —— 这个方法的"越权"被调用点约束住。
     */
    @Update("UPDATE bw_auth_organization SET deleted = 0 WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);

}
