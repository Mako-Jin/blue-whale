package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.BrandConfigAggregate;

import java.util.List;
import java.util.Optional;

/**
 * BrandConfig 聚合仓储端口（权限中心扩展 M0）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * <p>{@code sys_brand_config} 为全局表，登录页解析发生在未认证上下文，本仓储不参与租户行级过滤。</p>
 *
 * @author BlueWhale
 */
public interface BrandConfigRepository {

    /**
     * 保存品牌配置（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param brand 品牌配置聚合
     * @return 回填主键与时间戳后的聚合
     */
    BrandConfigAggregate save(BrandConfigAggregate brand);

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 品牌配置；不存在时返回空
     */
    Optional<BrandConfigAggregate> findById(Long id);

    /**
     * 按唯一寻址键查询
     *
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     * @param domain 绑定域名（空串表示默认品牌）
     * @return 品牌配置；不存在时返回空
     */
    Optional<BrandConfigAggregate> findByTarget(String targetType, Long targetId, String domain);

    /**
     * 登录页品牌解析：按「租户+域名 → 租户默认 → 平台默认」的优先级取启用品牌
     *
     * @param targetType 请求作用域类型
     * @param targetId 请求作用域 ID
     * @param domain 请求域名（可空）
     * @return 命中的品牌配置；无匹配时返回空
     */
    Optional<BrandConfigAggregate> findForResolve(String targetType, Long targetId, String domain);

    /**
     * 逻辑删除品牌配置
     *
     * @param id 主键
     */
    void deleteById(Long id);

    /**
     * 分页查询品牌配置（数据库侧 LIMIT/OFFSET 下推；按优先级升序、创建时间降序）
     *
     * @param current 当前页码（从 1 起）
     * @param size 每页条数
     * @param keyword 品牌名称/域名模糊匹配关键字（可空）
     * @param targetType 作用域类型精确过滤（可空）
     * @param status 状态精确过滤（可空）
     * @return 品牌分页结果
     */
    BrandPage page(long current, long size, String keyword, String targetType, Integer status);

    /**
     * 品牌分页结果：领域层自有分页载体
     *
     * @param records 当前页品牌聚合列表
     * @param total 总记录数
     */
    record BrandPage(List<BrandConfigAggregate> records, long total) {
    }
}
