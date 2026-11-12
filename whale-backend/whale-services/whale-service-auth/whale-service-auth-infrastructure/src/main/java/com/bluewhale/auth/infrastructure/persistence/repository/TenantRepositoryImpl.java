package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.auth.domain.aggregate.TenantAggregate;
import com.bluewhale.auth.domain.repository.TenantRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysTenantPo;
import com.bluewhale.auth.infrastructure.persistence.assembler.TenantPersistenceAssembler;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * {@link TenantRepository} 的 MyBatis-Plus 实现（Wave 5.54）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {

    private final SysTenantMapper tenantMapper;

    @Override
    public TenantAggregate save(TenantAggregate tenant) {
        SysTenantPo po = TenantPersistenceAssembler.toPo(tenant);
        if (po.getId() == null) {
            tenantMapper.insert(po);
        } else {
            tenantMapper.updateById(po);
        }
        return TenantPersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<TenantAggregate> findByCode(String tenantCode) {
        SysTenantPo po = tenantMapper.selectOne(new LambdaQueryWrapper<SysTenantPo>()
                .eq(SysTenantPo::getTenantCode, tenantCode)
                .last("limit 1"));
        return Optional.ofNullable(po).map(TenantPersistenceAssembler::toDomain);
    }

    @Override
    public Optional<TenantAggregate> findById(Long id) {
        SysTenantPo po = tenantMapper.selectById(id);
        return Optional.ofNullable(po).map(TenantPersistenceAssembler::toDomain);
    }

    @Override
    public List<TenantAggregate> findAll() {
        List<SysTenantPo> pos = tenantMapper.selectList(new LambdaQueryWrapper<SysTenantPo>()
                .orderByDesc(SysTenantPo::getCreateTime));
        return pos.stream().map(TenantPersistenceAssembler::toDomain).toList();
    }

    @Override
    public TenantPage page(long current, long size, String keyword, Integer status) {
        LambdaQueryWrapper<SysTenantPo> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SysTenantPo::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            // 数据库排序规则默认大小写不敏感，语义与原内存过滤（lower contains）一致；
            // LIKE 关键字转义反斜杠/百分号/下划线，防止通配符被当作模式展开导致全表扫描
            String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            wrapper.and(w -> w.like(SysTenantPo::getTenantCode, escaped)
                    .or().like(SysTenantPo::getTenantName, escaped));
        }
        wrapper.orderByDesc(SysTenantPo::getCreateTime);
        Page<SysTenantPo> poPage = tenantMapper.selectPage(Page.of(current, size), wrapper);
        List<TenantAggregate> records = poPage.getRecords().stream()
                .map(TenantPersistenceAssembler::toDomain)
                .toList();
        return new TenantPage(records, poPage.getTotal());
    }
}
