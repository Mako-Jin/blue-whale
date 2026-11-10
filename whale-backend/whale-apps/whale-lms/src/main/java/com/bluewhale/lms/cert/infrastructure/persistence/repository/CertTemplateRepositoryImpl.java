package com.bluewhale.lms.cert.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.cert.domain.model.CertTemplate;
import com.bluewhale.lms.cert.domain.model.CertTemplateQuery;
import com.bluewhale.lms.cert.domain.model.CertTemplateStatus;
import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.repository.CertTemplateRepository;
import com.bluewhale.lms.cert.infrastructure.persistence.entity.CertTemplateEntity;
import com.bluewhale.lms.cert.infrastructure.persistence.mapper.CertTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 证书模板仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CertTemplateRepositoryImpl implements CertTemplateRepository {

    private final CertTemplateMapper certTemplateMapper;

    @Override
    public List<CertTemplate> page(CertTemplateQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<CertTemplateEntity> page = certTemplateMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CertTemplateQuery query) {
        return certTemplateMapper.selectCount(filter(query));
    }

    @Override
    public Optional<CertTemplate> findById(Long id) {
        return Optional.ofNullable(certTemplateMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<CertTemplate> findByCode(String templateCode) {
        return Optional.ofNullable(certTemplateMapper.selectOne(new LambdaQueryWrapper<CertTemplateEntity>()
                        .eq(CertTemplateEntity::getTemplateCode, templateCode)))
                .map(this::toDomain);
    }

    @Override
    public Long save(CertTemplate template) {
        CertTemplateEntity entity = toEntity(template);
        if (template.getId() == null) {
            certTemplateMapper.insert(entity);
        } else {
            certTemplateMapper.updateById(entity);
        }
        template.setId(entity.getId());
        template.setCreateTime(entity.getCreateTime());
        template.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public void deleteById(Long id) {
        certTemplateMapper.deleteById(id);
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<CertTemplateEntity> filter(CertTemplateQuery query) {
        LambdaQueryWrapper<CertTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CertTemplateEntity::getTemplateCode, keyword)
                    .or().like(CertTemplateEntity::getName, keyword));
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(CertTemplateEntity::getCategory, query.getCategory().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CertTemplateEntity::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(CertTemplateEntity::getUpdateTime);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 模板
     */
    private CertTemplate toDomain(CertTemplateEntity entity) {
        CertTemplate template = new CertTemplate();
        template.setId(entity.getId());
        template.setTemplateCode(entity.getTemplateCode());
        template.setName(entity.getName());
        template.setCategory(entity.getCategory());
        template.setIssueBasis(CertSource.parse(entity.getIssueBasis()));
        template.setValidMonths(entity.getValidMonths() == null ? 0 : entity.getValidMonths());
        template.setCertPrefix(entity.getCertPrefix());
        template.setContent(entity.getContent());
        template.setStatus(CertTemplateStatus.parse(entity.getStatus()));
        template.setRemark(entity.getRemark());
        template.setCreateTime(entity.getCreateTime());
        template.setUpdateTime(entity.getUpdateTime());
        return template;
    }

    /**
     * 领域转实体。
     *
     * @param template 模板
     * @return 实体
     */
    private CertTemplateEntity toEntity(CertTemplate template) {
        CertTemplateEntity entity = new CertTemplateEntity();
        entity.setId(template.getId());
        entity.setTemplateCode(template.getTemplateCode());
        entity.setName(template.getName() == null ? null : template.getName().trim());
        entity.setCategory(template.getCategory());
        entity.setIssueBasis(template.getIssueBasis() == null ? null : template.getIssueBasis().name());
        entity.setValidMonths(template.getValidMonths());
        entity.setCertPrefix(template.getCertPrefix());
        entity.setContent(template.getContent());
        entity.setStatus(template.getStatus() == null ? null : template.getStatus().name());
        entity.setRemark(template.getRemark());
        entity.setCreateTime(template.getCreateTime());
        entity.setUpdateTime(template.getUpdateTime());
        return entity;
    }
}
