package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.PermissionStrategyAggregate;
import com.bluewhale.auth.domain.model.vo.StrategyCondition;
import com.bluewhale.auth.domain.repository.PermissionStrategyRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.PermissionStrategyPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysPermissionStrategyMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysPermissionStrategyPo;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 权限策略仓储 MyBatis-Plus 实现（权限中心扩展 M3）。
 *
 * <p>逻辑删除由 {@code deleted} 列承担（MyBatis-Plus 全局配置）；{@code roleIds}/
 * {@code resourceIds}/{@code conditionJson} 三列 JSON 互转委托装配器；
 * {@code clearDefault} 用 LambdaUpdateWrapper 定点清默认标记。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PermissionStrategyRepositoryImpl implements PermissionStrategyRepository {

    private final SysPermissionStrategyMapper strategyMapper;

    /**
     * 保存策略聚合：无主键新增（回填 ID），有主键按 ID 更新
     *
     * @param strategy 策略聚合
     * @return 回填主键后的聚合
     */
    @Override
    public PermissionStrategyAggregate save(PermissionStrategyAggregate strategy) {
        SysPermissionStrategyPo po = PermissionStrategyPersistenceAssembler.toPo(strategy);
        if (po.getId() == null) {
            strategyMapper.insert(po);
        } else {
            strategyMapper.updateById(po);
        }
        return PermissionStrategyPersistenceAssembler.toDomain(po);
    }

    /**
     * 按主键查询策略
     *
     * @param id 主键
     * @return 策略聚合；不存在时返回空
     */
    @Override
    public Optional<PermissionStrategyAggregate> findById(Long id) {
        SysPermissionStrategyPo po = strategyMapper.selectOne(new LambdaQueryWrapper<SysPermissionStrategyPo>()
                .eq(SysPermissionStrategyPo::getId, id)
                .eq(SysPermissionStrategyPo::getTenantId, TenantContext.requireId()));
        return Optional.ofNullable(po).map(PermissionStrategyPersistenceAssembler::toDomain);
    }

    /**
     * 判断策略编码在租户内是否已存在（排除指定策略自身）
     *
     * @param tenantId 租户 ID
     * @param strategyCode 策略编码
     * @param excludeId 排除的策略 ID（新建传 {@code null}）
     * @return 已存在返回 true
     */
    @Override
    public boolean existsByCode(Long tenantId, String strategyCode, Long excludeId) {
        LambdaQueryWrapper<SysPermissionStrategyPo> wrapper = new LambdaQueryWrapper<SysPermissionStrategyPo>()
                .eq(SysPermissionStrategyPo::getTenantId, tenantId)
                .eq(SysPermissionStrategyPo::getStrategyCode, strategyCode);
        if (excludeId != null) {
            wrapper.ne(SysPermissionStrategyPo::getId, excludeId);
        }
        Long count = strategyMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 清除租户内其它默认策略（{@code excludeStrategyId} 为空表示全部清除）
     *
     * @param tenantId 租户 ID
     * @param excludeStrategyId 保持默认的策略 ID（{@code null} 表示全部清除）
     */
    @Override
    public void clearDefault(Long tenantId, Long excludeStrategyId) {
        LambdaUpdateWrapper<SysPermissionStrategyPo> update = new LambdaUpdateWrapper<SysPermissionStrategyPo>()
                .set(SysPermissionStrategyPo::getIsDefault, 0)
                .eq(SysPermissionStrategyPo::getTenantId, tenantId)
                .eq(SysPermissionStrategyPo::getIsDefault, 1);
        if (excludeStrategyId != null) {
            update.ne(SysPermissionStrategyPo::getId, excludeStrategyId);
        }
        strategyMapper.update(null, update);
    }

    /**
     * 分页查询策略（keyword 模糊匹配编码/名称，按 priority 升序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字（可空）
     * @param strategyType 类型过滤（可空）
     * @return 策略分页结果
     */
    @Override
    public StrategyPage page(long current, long size, String keyword, String strategyType) {
        LambdaQueryWrapper<SysPermissionStrategyPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermissionStrategyPo::getTenantId, TenantContext.requireId());
        if (StringUtils.hasText(keyword)) {
            String escaped = escapeLike(keyword);
            wrapper.and(w -> w.like(SysPermissionStrategyPo::getStrategyCode, escaped)
                    .or().like(SysPermissionStrategyPo::getStrategyName, escaped));
        }
        if (StringUtils.hasText(strategyType)) {
            wrapper.eq(SysPermissionStrategyPo::getStrategyType, strategyType);
        }
        wrapper.orderByAsc(SysPermissionStrategyPo::getPriority)
                .orderByDesc(SysPermissionStrategyPo::getCreateTime);
        Page<SysPermissionStrategyPo> page = strategyMapper.selectPage(Page.of(current, size), wrapper);
        List<PermissionStrategyAggregate> records = page.getRecords().stream()
                .map(PermissionStrategyPersistenceAssembler::toDomain).toList();
        return new StrategyPage(records, page.getTotal());
    }

    /**
     * 删除策略（逻辑删除）
     *
     * @param id 策略 ID
     */
    @Override
    public void deleteById(Long id) {
        strategyMapper.delete(new LambdaQueryWrapper<SysPermissionStrategyPo>()
                .eq(SysPermissionStrategyPo::getId, id)
                .eq(SysPermissionStrategyPo::getTenantId, TenantContext.requireId()));
    }

    /**
     * 查询租户内全部启用策略（按 priority 升序）
     *
     * @param tenantId 租户 ID
     * @return 启用策略列表
     */
    @Override
    public List<PermissionStrategyAggregate> listEnabledByTenant(Long tenantId) {
        return strategyMapper.selectList(new LambdaQueryWrapper<SysPermissionStrategyPo>()
                        .eq(SysPermissionStrategyPo::getTenantId, tenantId)
                        .eq(SysPermissionStrategyPo::getStatus, PermissionStrategyAggregate.STATUS_ENABLED)
                        .orderByAsc(SysPermissionStrategyPo::getPriority))
                .stream().map(PermissionStrategyPersistenceAssembler::toDomain).toList();
    }

    /**
     * 解析策略附加条件（JSON 反序列化；存储为空时返回空列表）
     *
     * @param conditionJson 条件 JSON（可空）
     * @return 条件列表
     */
    @Override
    public List<StrategyCondition> parseConditions(String conditionJson) {
        return PermissionStrategyPersistenceAssembler.parseConditions(conditionJson);
    }

    /**
     * 序列化附加条件为 JSON（应用层 VO 透传用）
     *
     * @param conditions 条件列表（可空）
     * @return JSON 字符串；空列表返回 {@code null}
     */
    @Override
    public String serializeConditions(List<StrategyCondition> conditions) {
        return PermissionStrategyPersistenceAssembler.serializeConditions(conditions);
    }

    /**
     * LIKE 关键字转义：反斜杠、百分号、下划线（MySQL LIKE 默认以反斜杠为转义符）
     *
     * @param keyword 原始关键字
     * @return 转义后的关键字
     */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
