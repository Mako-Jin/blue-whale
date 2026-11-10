package com.bluewhale.lms.cert.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.CertStatus;
import com.bluewhale.lms.cert.domain.model.Certificate;
import com.bluewhale.lms.cert.domain.model.CertificateQuery;
import com.bluewhale.lms.cert.domain.repository.CertificateRepository;
import com.bluewhale.lms.cert.infrastructure.persistence.entity.CertificateEntity;
import com.bluewhale.lms.cert.infrastructure.persistence.mapper.CertificateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 证书仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CertificateRepositoryImpl implements CertificateRepository {

    /** 落库状态「有效」：其余状态与时间无关 */
    private static final String STORED_ACTIVE = "ACTIVE";

    private final CertificateMapper certificateMapper;

    @Override
    public List<Certificate> page(CertificateQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<CertificateEntity> page = certificateMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CertificateQuery query) {
        return certificateMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Certificate> findById(Long id) {
        return Optional.ofNullable(certificateMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Certificate> findByNo(String certNo) {
        return Optional.ofNullable(certificateMapper.selectOne(new LambdaQueryWrapper<CertificateEntity>()
                        .eq(CertificateEntity::getCertNo, certNo)))
                .map(this::toDomain);
    }

    @Override
    public Optional<Certificate> findFirstIssue(Long templateId, Long userId, CertSource sourceType, Long sourceId) {
        List<CertificateEntity> list = certificateMapper.selectList(new LambdaQueryWrapper<CertificateEntity>()
                .eq(CertificateEntity::getTemplateId, templateId)
                .eq(CertificateEntity::getUserId, userId)
                .eq(CertificateEntity::getSourceType, sourceType.name())
                .eq(CertificateEntity::getSourceId, sourceId)
                .eq(CertificateEntity::getRenewFromId, Certificate.NO_RENEW_SOURCE));
        return list.stream().findFirst().map(this::toDomain);
    }

    @Override
    public int findMaxSeqNo(int year) {
        List<CertificateEntity> list = certificateMapper.selectList(new LambdaQueryWrapper<CertificateEntity>()
                .eq(CertificateEntity::getIssueYear, year)
                .orderByDesc(CertificateEntity::getSeqNo)
                .last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getSeqNo() == null ? 0 : list.get(0).getSeqNo();
    }

    @Override
    public Long save(Certificate certificate) {
        CertificateEntity entity = toEntity(certificate);
        if (certificate.getId() == null) {
            certificateMapper.insert(entity);
        } else {
            certificateMapper.updateById(entity);
        }
        certificate.setId(entity.getId());
        certificate.setCreateTime(entity.getCreateTime());
        certificate.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public List<Certificate> pageByUser(Long userId, long offset, int size) {
        CertificateQuery query = new CertificateQuery();
        query.setUserId(userId);
        return page(query, offset, size);
    }

    @Override
    public long countByUser(Long userId) {
        CertificateQuery query = new CertificateQuery();
        query.setUserId(userId);
        return count(query);
    }

    @Override
    public long countByTemplate(Long templateId) {
        return certificateMapper.selectCount(new LambdaQueryWrapper<CertificateEntity>()
                .eq(CertificateEntity::getTemplateId, templateId));
    }

    /**
     * 组装查询条件。
     *
     * <p>{@code ACTIVE} / {@code EXPIRED} 不是落库值，而是「落库有效 + 时间条件」的组合，
     * 这里把筛选意图翻译成 SQL，避免调用方各自拼时间。</p>
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<CertificateEntity> filter(CertificateQuery query) {
        LambdaQueryWrapper<CertificateEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CertificateEntity::getCertNo, keyword)
                    .or().like(CertificateEntity::getUsername, keyword)
                    .or().like(CertificateEntity::getTemplateName, keyword));
        }
        if (query.getTemplateId() != null) {
            wrapper.eq(CertificateEntity::getTemplateId, query.getTemplateId());
        }
        if (query.getUserId() != null) {
            wrapper.eq(CertificateEntity::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(CertificateEntity::getSourceType, query.getSourceType().trim());
        }
        applyStatus(wrapper, query.getStatus());
        wrapper.orderByDesc(CertificateEntity::getIssuedAt);
        return wrapper;
    }

    /**
     * 应用展示状态筛选。
     *
     * @param wrapper 包装器
     * @param status 展示状态
     */
    private void applyStatus(LambdaQueryWrapper<CertificateEntity> wrapper, String status) {
        if (!StringUtils.hasText(status)) {
            return;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        if (CertStatus.EXPIRED.name().equals(normalized)) {
            wrapper.eq(CertificateEntity::getStatus, STORED_ACTIVE)
                    .isNotNull(CertificateEntity::getValidTo)
                    .lt(CertificateEntity::getValidTo, now);
            return;
        }
        if (CertStatus.ACTIVE.name().equals(normalized)) {
            wrapper.eq(CertificateEntity::getStatus, STORED_ACTIVE)
                    .and(w -> w.isNull(CertificateEntity::getValidTo)
                            .or().ge(CertificateEntity::getValidTo, now));
            return;
        }
        wrapper.eq(CertificateEntity::getStatus, normalized);
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 证书
     */
    private Certificate toDomain(CertificateEntity entity) {
        Certificate certificate = new Certificate();
        certificate.setId(entity.getId());
        certificate.setCertNo(entity.getCertNo());
        certificate.setTemplateId(entity.getTemplateId());
        certificate.setTemplateName(entity.getTemplateName());
        certificate.setCategory(entity.getCategory());
        certificate.setUserId(entity.getUserId());
        certificate.setUsername(entity.getUsername());
        certificate.setSourceType(CertSource.parse(entity.getSourceType()));
        certificate.setSourceId(entity.getSourceId());
        certificate.setSourceTitle(entity.getSourceTitle());
        certificate.setIssueYear(entity.getIssueYear());
        certificate.setSeqNo(entity.getSeqNo());
        certificate.setIssuedAt(entity.getIssuedAt());
        certificate.setValidFrom(entity.getValidFrom());
        certificate.setValidTo(entity.getValidTo());
        certificate.setStatus(CertStatus.parse(entity.getStatus()));
        certificate.setRevokedAt(entity.getRevokedAt());
        certificate.setRevokedBy(entity.getRevokedBy());
        certificate.setRevokeReason(entity.getRevokeReason());
        certificate.setRenewFromId(entity.getRenewFromId() == null
                ? Certificate.NO_RENEW_SOURCE : entity.getRenewFromId());
        certificate.setRemark(entity.getRemark());
        certificate.setCreateTime(entity.getCreateTime());
        certificate.setUpdateTime(entity.getUpdateTime());
        return certificate;
    }

    /**
     * 领域转实体。
     *
     * @param certificate 证书
     * @return 实体
     */
    private CertificateEntity toEntity(Certificate certificate) {
        CertificateEntity entity = new CertificateEntity();
        entity.setId(certificate.getId());
        entity.setCertNo(certificate.getCertNo());
        entity.setTemplateId(certificate.getTemplateId());
        entity.setTemplateName(certificate.getTemplateName());
        entity.setCategory(certificate.getCategory());
        entity.setUserId(certificate.getUserId());
        entity.setUsername(certificate.getUsername());
        entity.setSourceType(certificate.getSourceType() == null ? null : certificate.getSourceType().name());
        entity.setSourceId(certificate.getSourceId() == null ? 0L : certificate.getSourceId());
        entity.setSourceTitle(certificate.getSourceTitle());
        entity.setIssueYear(certificate.getIssueYear());
        entity.setSeqNo(certificate.getSeqNo());
        entity.setIssuedAt(certificate.getIssuedAt());
        entity.setValidFrom(certificate.getValidFrom());
        entity.setValidTo(certificate.getValidTo());
        entity.setStatus(certificate.getStatus() == null ? null : certificate.getStatus().name());
        entity.setRevokedAt(certificate.getRevokedAt());
        entity.setRevokedBy(certificate.getRevokedBy());
        entity.setRevokeReason(certificate.getRevokeReason());
        entity.setRenewFromId(certificate.getRenewFromId() == null
                ? Certificate.NO_RENEW_SOURCE : certificate.getRenewFromId());
        entity.setRemark(certificate.getRemark());
        entity.setCreateTime(certificate.getCreateTime());
        entity.setUpdateTime(certificate.getUpdateTime());
        return entity;
    }
}
