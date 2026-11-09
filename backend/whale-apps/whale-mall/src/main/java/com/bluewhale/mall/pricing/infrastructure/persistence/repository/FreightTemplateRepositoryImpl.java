package com.bluewhale.mall.pricing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.pricing.domain.model.FreightChargeType;
import com.bluewhale.mall.pricing.domain.model.FreightTemplate;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateQuery;
import com.bluewhale.mall.pricing.domain.model.FreightTemplateStatus;
import com.bluewhale.mall.pricing.domain.repository.FreightTemplateRepository;
import com.bluewhale.mall.pricing.infrastructure.persistence.entity.FreightTemplateEntity;
import com.bluewhale.mall.pricing.infrastructure.persistence.mapper.FreightTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 运费模板仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class FreightTemplateRepositoryImpl implements FreightTemplateRepository {

    private final FreightTemplateMapper freightTemplateMapper;

    @Override
    public List<FreightTemplate> page(FreightTemplateQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<FreightTemplateEntity> page = freightTemplateMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(FreightTemplateQuery query) {
        return freightTemplateMapper.selectCount(filter(query));
    }

    @Override
    public Optional<FreightTemplate> findById(Long id) {
        return Optional.ofNullable(freightTemplateMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsName(String templateName, Long excludeId) {
        if (!StringUtils.hasText(templateName)) {
            return false;
        }
        return freightTemplateMapper.selectCount(new LambdaQueryWrapper<FreightTemplateEntity>()
                .eq(FreightTemplateEntity::getTemplateName, templateName.trim())
                .ne(excludeId != null, FreightTemplateEntity::getId, excludeId)) > 0;
    }

    @Override
    public Long save(FreightTemplate template) {
        FreightTemplateEntity entity = toEntity(template);
        if (entity.getId() == null) {
            freightTemplateMapper.insert(entity);
        } else {
            freightTemplateMapper.updateById(entity);
        }
        template.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void updateStatus(Long id, String status) {
        FreightTemplateEntity entity = new FreightTemplateEntity();
        entity.setId(id);
        entity.setStatus(status);
        freightTemplateMapper.updateById(entity);
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<FreightTemplateEntity> filter(FreightTemplateQuery query) {
        LambdaQueryWrapper<FreightTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            wrapper.like(FreightTemplateEntity::getTemplateName, query.getKeyword().trim());
        }
        if (query != null && StringUtils.hasText(query.getChargeType())) {
            wrapper.eq(FreightTemplateEntity::getChargeType,
                    query.getChargeType().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && StringUtils.hasText(query.getStatus())) {
            wrapper.eq(FreightTemplateEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByDesc(FreightTemplateEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private FreightTemplate toDomain(FreightTemplateEntity entity) {
        FreightTemplate template = new FreightTemplate();
        template.setId(entity.getId());
        template.setTemplateName(entity.getTemplateName());
        template.setChargeType(parseChargeType(entity.getChargeType()));
        template.setBaseAmount(entity.getBaseAmount());
        template.setFreeThreshold(entity.getFreeThreshold());
        template.setPiecePrice(entity.getPiecePrice());
        template.setPieceFreeThreshold(entity.getPieceFreeThreshold());
        template.setStatus(FreightTemplateStatus.parse(entity.getStatus()));
        template.setRemark(entity.getRemark());
        template.setCreateTime(entity.getCreateTime());
        template.setUpdateTime(entity.getUpdateTime());
        return template;
    }

    /**
     * 领域模型转实体。
     *
     * @param template 领域模型
     * @return 实体
     */
    private FreightTemplateEntity toEntity(FreightTemplate template) {
        FreightTemplateEntity entity = new FreightTemplateEntity();
        entity.setId(template.getId());
        entity.setTemplateName(template.getTemplateName());
        entity.setChargeType(template.getChargeType() == null ? null : template.getChargeType().name());
        entity.setBaseAmount(template.getBaseAmount());
        entity.setFreeThreshold(template.getFreeThreshold());
        entity.setPiecePrice(template.getPiecePrice());
        entity.setPieceFreeThreshold(template.getPieceFreeThreshold());
        entity.setStatus(template.getStatus() == null
                ? FreightTemplateStatus.ACTIVE.name() : template.getStatus().name());
        entity.setRemark(template.getRemark());
        return entity;
    }

    /**
     * 解析计费方式文本。
     *
     * @param value 文本
     * @return 计费方式；无法识别时回落到固定运费（保守：宁可收费不漏收）
     */
    private FreightChargeType parseChargeType(String value) {
        if (!StringUtils.hasText(value)) {
            return FreightChargeType.FREE;
        }
        try {
            return FreightChargeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return FreightChargeType.FIXED;
        }
    }
}
