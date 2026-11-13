package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.PermissionStrategyAggregate;
import com.bluewhale.auth.domain.model.vo.StrategyCondition;

import java.util.List;
import java.util.Optional;

/**
 * 权限策略仓储端口（权限中心扩展 M3）：契约定义于领域层，由 infrastructure 适配实现。
 *
 * @author BlueWhale
 */
public interface PermissionStrategyRepository {

    /**
     * 保存策略聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param strategy 策略聚合
     * @return 回填主键后的聚合
     */
    PermissionStrategyAggregate save(PermissionStrategyAggregate strategy);

    /**
     * 按主键查询策略
     *
     * @param id 主键
     * @return 策略聚合；不存在时返回空
     */
    Optional<PermissionStrategyAggregate> findById(Long id);

    /**
     * 判断策略编码在租户内是否已存在（排除指定策略自身）
     *
     * <p>并发说明：先查后插的友好预检，唯一性由 DB 约束 {@code uk_strategy_code} 兜底。</p>
     *
     * @param tenantId 租户 ID
     * @param strategyCode 策略编码
     * @param excludeId 排除的策略 ID（更新场景传自身主键，新建传 {@code null}）
     * @return 已存在返回 true
     */
    boolean existsByCode(Long tenantId, String strategyCode, Long excludeId);

    /**
     * 清除租户内其它默认策略（保证「同租户仅一条 is_default」的编排第一步）
     *
     * <p>须在事务内调用、先于 {@code save} 执行。</p>
     *
     * @param tenantId 租户 ID
     * @param excludeStrategyId 保持默认的策略 ID（{@code null} 表示全部清除）
     */
    void clearDefault(Long tenantId, Long excludeStrategyId);

    /**
     * 分页查询策略（keyword 模糊匹配编码/名称，按 priority 升序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字（可空）
     * @param strategyType 类型过滤（可空）
     * @return 策略分页结果
     */
    StrategyPage page(long current, long size, String keyword, String strategyType);

    /**
     * 删除策略（逻辑删除）
     *
     * @param id 策略 ID
     */
    void deleteById(Long id);

    /**
     * 查询租户内全部启用策略（按 priority 升序，供授权链路消费）
     *
     * @param tenantId 租户 ID
     * @return 启用策略列表
     */
    List<PermissionStrategyAggregate> listEnabledByTenant(Long tenantId);

    /**
     * 解析策略附加条件（JSON 反序列化；存储为空时返回空列表）
     *
     * @param conditionJson 条件 JSON（可空）
     * @return 条件列表
     */
    List<StrategyCondition> parseConditions(String conditionJson);

    /**
     * 序列化附加条件为 JSON（应用层 VO 透传用）
     *
     * @param conditions 条件列表（可空）
     * @return JSON 字符串；空列表返回 {@code null}
     */
    String serializeConditions(List<StrategyCondition> conditions);

    /**
     * 策略分页结果：领域层自有分页载体
     *
     * @param records 当前页策略聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record StrategyPage(List<PermissionStrategyAggregate> records, long total) {
    }
}
