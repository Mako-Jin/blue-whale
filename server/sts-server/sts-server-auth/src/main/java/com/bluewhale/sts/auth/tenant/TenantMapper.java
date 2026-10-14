package com.bluewhale.sts.auth.tenant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.tenant.TenantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户仓储。
 *
 * <p>{@code @Mapper} 是**必须显式标注**的：本模块的 Mapper 扫描配了
 * {@code annotationClass = Mapper.class}，不标注就不会被注册 ——
 * 这是有意为之，避免包内任何接口都被当成 Mapper。
 *
 * <p>除继承来的通用 CRUD 外，本接口有一个**刻意绕过 {@code @TableLogic} 的例外**
 * （手写 SQL）：{@code uk_bw_auth_tenant_code} 建在 {@code tenant_code} 单列上、
 * **不含 {@code deleted}**，所以软删的租户仍然占着这个编码，而自动生成的查询
 * 一律追加 {@code deleted = 0}、看不见这个占位。全仓六个被索引逼着的唯一编码列
 * 连同各自答案的清单见 {@code server/README.md} 的「唯一编码列与它们的软删语义」。
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

    /**
     * 按租户编码取一行，**包括已被逻辑删除的那些**。
     *
     * <p>手写 SQL 不会被 {@code @TableLogic} 改写，所以这里能看见软删行。
     * 建租户前用它回答"这个编码是真的没人用，还是只是被删过"。
     *
     * <p><b>与另外四个域（资源 / 角色 / 组织 / 用户组）不同，租户这一列看见软删行之后
     * 是拒绝、不是复活</b> —— 理由在 {@code TenantService#create} 上，简短版是
     * "复活一个已删租户会把它的用户一起放回来"。所以本接口**没有**
     * {@code restoreDeletedById}：那个方法在这里没有被授权使用的调用点。
     */
    @Select("""
            SELECT * FROM bw_auth_tenant
            WHERE tenant_code = #{tenantCode}
            """)
    TenantEntity selectAnyByCode(@Param("tenantCode") String tenantCode);

}
