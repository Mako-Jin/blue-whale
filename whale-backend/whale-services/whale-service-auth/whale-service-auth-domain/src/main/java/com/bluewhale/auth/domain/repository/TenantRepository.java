package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.Tenant;

import java.util.List;
import java.util.Optional;

/**
 * Tenant 聚合仓储端口（Wave 5.54）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>{@code sys_tenant} 为全局表，本仓储不参与租户行级过滤。</p>
 *
 * @author BlueWhale
 */
public interface TenantRepository {

    /**
     * 保存租户聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param tenant 租户聚合
     * @return 回填主键与时间戳后的租户聚合
     */
    Tenant save(Tenant tenant);

    /**
     * 按租户编码查询租户
     *
     * @param tenantCode 租户编码
     * @return 租户聚合；不存在时返回空
     */
    Optional<Tenant> findByCode(String tenantCode);

    /**
     * 按租户 ID 查询租户
     *
     * @param id 租户 ID
     * @return 租户聚合；不存在时返回空
     */
    Optional<Tenant> findById(Long id);

    /**
     * 查询全部租户（按创建时间降序）
     *
     * @return 租户聚合列表
     */
    List<Tenant> findAll();

    /**
     * 分页查询租户（数据库侧 LIMIT/OFFSET 下推，避免全量加载后内存切片；按创建时间降序）
     *
     * @param current 当前页码（从 1 起）
     * @param size 每页条数
     * @param keyword 租户编码或名称模糊匹配关键字（可空）
     * @param status 状态精确过滤（可空）
     * @return 租户分页结果
     */
    TenantPage page(long current, long size, String keyword, Integer status);

    /**
     * 租户分页结果：领域层自有分页载体，避免向领域层泄漏 MyBatis 分页类型。
     *
     * @param records 当前页租户聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record TenantPage(List<Tenant> records, long total) {
    }
}
