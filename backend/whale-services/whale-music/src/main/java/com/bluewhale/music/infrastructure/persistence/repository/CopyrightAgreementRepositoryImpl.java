package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.music.domain.model.CopyrightAgreement;
import com.bluewhale.music.domain.repository.CopyrightAgreementRepository;
import com.bluewhale.music.infrastructure.persistence.entity.AgreementEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.AgreementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 版权协议签署仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CopyrightAgreementRepositoryImpl implements CopyrightAgreementRepository {

    private final AgreementMapper agreementMapper;

    @Override
    public Optional<CopyrightAgreement> findByUserAndVersion(Long userId, String agreementVersion) {
        AgreementEntity entity = agreementMapper.selectOne(new LambdaQueryWrapper<AgreementEntity>()
                .eq(AgreementEntity::getUserId, userId)
                .eq(AgreementEntity::getAgreementVersion, agreementVersion));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public void insert(CopyrightAgreement agreement) {
        AgreementEntity entity = new AgreementEntity();
        entity.setUserId(agreement.getUserId());
        entity.setAgreementVersion(agreement.getAgreementVersion());
        entity.setAcceptedAt(agreement.getAcceptedAt());
        entity.setClientIp(agreement.getClientIp());
        entity.setUserAgent(agreement.getUserAgent());
        agreementMapper.insert(entity);
        agreement.setId(entity.getId());
    }

    /**
     * 将实体转换为领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CopyrightAgreement toDomain(AgreementEntity entity) {
        CopyrightAgreement agreement = new CopyrightAgreement();
        agreement.setId(entity.getId());
        agreement.setUserId(entity.getUserId());
        agreement.setAgreementVersion(entity.getAgreementVersion());
        agreement.setAcceptedAt(entity.getAcceptedAt());
        agreement.setClientIp(entity.getClientIp());
        agreement.setUserAgent(entity.getUserAgent());
        return agreement;
    }
}
