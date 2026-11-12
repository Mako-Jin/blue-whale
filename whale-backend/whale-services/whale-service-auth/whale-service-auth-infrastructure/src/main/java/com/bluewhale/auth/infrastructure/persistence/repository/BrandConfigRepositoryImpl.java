package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.BrandConfigAggregate;
import com.bluewhale.auth.domain.repository.BrandConfigRepository;
import com.bluewhale.auth.infrastructure.persistence.assembler.BrandConfigPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysBrandConfigMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysBrandConfigPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * {@link BrandConfigRepository} 的 MyBatis-Plus 实现（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class BrandConfigRepositoryImpl implements BrandConfigRepository {

    private final SysBrandConfigMapper brandConfigMapper;

    /**
     * 保存品牌配置
     *
     * @param brand 品牌配置聚合
     * @return 回填主键后的聚合
     */
    @Override
    public BrandConfigAggregate save(BrandConfigAggregate brand) {
        SysBrandConfigPo po = BrandConfigPersistenceAssembler.toPo(brand);
        if (po.getId() == null) {
            brandConfigMapper.insert(po);
        } else {
            brandConfigMapper.updateById(po);
        }
        return BrandConfigPersistenceAssembler.toDomain(po);
    }

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 品牌配置
     */
    @Override
    public Optional<BrandConfigAggregate> findById(Long id) {
        SysBrandConfigPo po = brandConfigMapper.selectById(id);
        return Optional.ofNullable(po).map(BrandConfigPersistenceAssembler::toDomain);
    }

    /**
     * 按唯一寻址键查询
     *
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     * @param domain 绑定域名
     * @return 品牌配置
     */
    @Override
    public Optional<BrandConfigAggregate> findByTarget(String targetType, Long targetId, String domain) {
        SysBrandConfigPo po = brandConfigMapper.selectOne(new LambdaQueryWrapper<SysBrandConfigPo>()
                .eq(SysBrandConfigPo::getTargetType, targetType)
                .eq(SysBrandConfigPo::getTargetId, targetId)
                .eq(SysBrandConfigPo::getDomain, domain == null ? "" : domain)
                .last("limit 1"));
        return Optional.ofNullable(po).map(BrandConfigPersistenceAssembler::toDomain);
    }

    /**
     * 登录页品牌解析：租户+域名 → 租户默认 → 平台默认
     *
     * <p>只返回启用品牌，并按 {@code priority} 升序取第一条；SQL 侧 {@code limit 1}，
     * 避免把整个品牌表读进内存。</p>
     *
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     * @param domain 请求域名
     * @return 命中的品牌配置
     */
    @Override
    public Optional<BrandConfigAggregate> findForResolve(String targetType, Long targetId, String domain) {
        String normalizedDomain = domain == null ? "" : domain.trim();
        LambdaQueryWrapper<SysBrandConfigPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysBrandConfigPo::getStatus, BrandConfigAggregate.STATUS_ENABLED);
        wrapper.and(w -> w
                .and(x -> x.eq(SysBrandConfigPo::getTargetType, targetType)
                        .eq(SysBrandConfigPo::getTargetId, targetId)
                        .eq(SysBrandConfigPo::getDomain, normalizedDomain))
                .or(x -> x.eq(SysBrandConfigPo::getTargetType, targetType)
                        .eq(SysBrandConfigPo::getTargetId, targetId)
                        .eq(SysBrandConfigPo::getDomain, ""))
                .or(x -> x.eq(SysBrandConfigPo::getTargetType, BrandConfigAggregate.TARGET_PLATFORM)
                        .eq(SysBrandConfigPo::getTargetId, 0L)
                        .eq(SysBrandConfigPo::getDomain, "")));
        wrapper.orderByAsc(SysBrandConfigPo::getPriority)
                .orderByDesc(SysBrandConfigPo::getUpdateTime)
                .last("limit 1");
        SysBrandConfigPo po = brandConfigMapper.selectOne(wrapper);
        return Optional.ofNullable(po).map(BrandConfigPersistenceAssembler::toDomain);
    }

    /**
     * 逻辑删除品牌配置
     *
     * @param id 主键
     */
    @Override
    public void deleteById(Long id) {
        brandConfigMapper.deleteById(id);
    }

    /**
     * 分页查询品牌配置（数据库侧分页）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字
     * @param targetType 作用域类型
     * @param status 状态
     * @return 品牌分页结果
     */
    @Override
    public BrandPage page(long current, long size, String keyword, String targetType, Integer status) {
        LambdaQueryWrapper<SysBrandConfigPo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(SysBrandConfigPo::getTargetType, targetType.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (status != null) {
            wrapper.eq(SysBrandConfigPo::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            // LIKE 关键字转义反斜杠/百分号/下划线，防止通配符被当作模式展开导致全表扫描
            String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            wrapper.and(w -> w.like(SysBrandConfigPo::getBrandName, escaped)
                    .or().like(SysBrandConfigPo::getDomain, escaped));
        }
        wrapper.orderByAsc(SysBrandConfigPo::getPriority).orderByDesc(SysBrandConfigPo::getCreateTime);
        Page<SysBrandConfigPo> poPage = brandConfigMapper.selectPage(Page.of(current, size), wrapper);
        List<BrandConfigAggregate> records = poPage.getRecords().stream()
                .map(BrandConfigPersistenceAssembler::toDomain)
                .toList();
        return new BrandPage(records, poPage.getTotal());
    }
}
