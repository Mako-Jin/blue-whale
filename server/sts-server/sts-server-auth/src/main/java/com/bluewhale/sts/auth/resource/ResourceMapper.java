package com.bluewhale.sts.auth.resource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 资源仓储。{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 *
 * <p>下面两个方法是**唯一两处刻意绕开 MyBatis-Plus 逻辑删除拦截**的 SQL，
 * 原因都是同一个：{@code uk_bw_auth_resource_code} 建立在 {@code resource_code} 单列上
 * （不含 {@code deleted}），所以"已逻辑删除的行"仍然占着那个标识。而 MyBatis-Plus 生成的
 * 每一条查询都会自动追加 {@code deleted = 0}，于是它**看不见**这行占位 ——
 * 表现为：删掉一个资源后再用同一个标识新建，唯一性检查说"没重复"，
 * 插入却撞键，抛出的 {@code DuplicateKeyException} 最终被兜底成
 * {@code 40900}（{@code ADR-0024} 之前是 500）。
 */
@Mapper
public interface ResourceMapper extends BaseMapper<ResourceEntity> {

    /**
     * 按资源标识查询，**包含已逻辑删除的行**。
     *
     * <p>手写 SQL 不会被 {@code @TableLogic} 改写，因此这里能看见软删行。
     * 建资源前用它判断"这个标识是真的没人用，还是只是被删过"——
     * 两者要给出不同的结果，见 {@code ResourceService#create}。
     */
    @Select("SELECT * FROM bw_auth_resource WHERE resource_code = #{resourceCode}")
    ResourceEntity selectAnyByCode(@Param("resourceCode") String resourceCode);

    /**
     * 把一行逻辑删除的记录恢复为未删除。
     *
     * <p><b>为什么必须手写：</b>{@code updateById} 会在 WHERE 上追加
     * {@code deleted = 0}，而目标行恰恰是 {@code deleted = 1} —— 永远匹配不到，
     * 且 MyBatis-Plus 对"影响 0 行"不报错，会静默地什么都没做。
     * 调 {@code updateById} 去复活一行已删除的记录，是这类静默失败的典型。
     *
     * <p>刻意不在此处维护 {@code update_by} / {@code update_time}：
     * 复活的调用方紧接着还会走一次正常的 {@code updateById}，
     * 那两个字段由 {@code AuthAuditMetaObjectHandler} 在那一步统一填。
     *
     * <p><b>这里是全仓唯一一条不带租户条件的复活语句</b>（{@code decisions/0079}）：
     * {@code bw_auth_resource.tenant_id} 可空，而 {@code NULL} 的含义是**平台级资源**
     * （对所有人可见）；写一个 {@code tenant_id = #{tenantId}} 会把平台级资源整类排除在外，
     * 那不是"更安全"，是**改错语义**。它与 {@code bw_audit_log} 那三条管道的判据一样，
     * 是"这张表没有'租户内的行'这个前提"，因此由规则 F 的射程**推导排除**、不进白名单。
     */
    @Update("UPDATE bw_auth_resource SET deleted = 0 WHERE id = #{id}")
    int restoreDeletedById(@Param("id") Long id);

}
