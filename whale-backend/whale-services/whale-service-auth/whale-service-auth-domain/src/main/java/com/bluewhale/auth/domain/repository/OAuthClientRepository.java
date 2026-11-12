package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.OAuthClientAggregate;

import java.util.List;
import java.util.Optional;

/**
 * OAuthClient 聚合仓储端口（权限中心扩展 M0）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>{@code sys_oauth_client} 为全局表，客户端注册与换票都不按租户隔离。</p>
 *
 * @author BlueWhale
 */
public interface OAuthClientRepository {

    /**
     * 保存客户端（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param client 客户端聚合
     * @return 回填主键与时间戳后的聚合
     */
    OAuthClientAggregate save(OAuthClientAggregate client);

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 客户端；不存在时返回空
     */
    Optional<OAuthClientAggregate> findById(Long id);

    /**
     * 按客户端标识查询（换票主查询入口）
     *
     * @param clientId 客户端标识
     * @return 客户端；不存在时返回空
     */
    Optional<OAuthClientAggregate> findByClientId(String clientId);

    /**
     * 判断客户端标识是否已存在
     *
     * @param clientId 客户端标识
     * @return 已存在返回 {@code true}
     */
    boolean existsByClientId(String clientId);

    /**
     * 分页查询客户端（数据库侧 LIMIT/OFFSET 下推；按创建时间降序）
     *
     * @param current 当前页码（从 1 起）
     * @param size 每页条数
     * @param keyword 客户端标识/名称模糊匹配关键字（可空）
     * @param status 状态精确过滤（可空）
     * @return 客户端分页结果
     */
    OAuthClientPage page(long current, long size, String keyword, Integer status);

    /**
     * 客户端分页结果：领域层自有分页载体
     *
     * @param records 当前页客户端聚合列表
     * @param total 总记录数
     */
    record OAuthClientPage(List<OAuthClientAggregate> records, long total) {
    }
}
