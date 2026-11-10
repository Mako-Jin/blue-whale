package com.bluewhale.video.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.video.domain.model.CopyrightSource;
import com.bluewhale.video.domain.model.UploadDeclaration;
import com.bluewhale.video.domain.repository.DeclarationRepository;
import com.bluewhale.video.infrastructure.persistence.entity.DeclarationEntity;
import com.bluewhale.video.infrastructure.persistence.mapper.DeclarationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 上传声明签署仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class DeclarationRepositoryImpl implements DeclarationRepository {

    private final DeclarationMapper declarationMapper;

    @Override
    public Optional<UploadDeclaration> find(Long userId, String version) {
        return Optional.ofNullable(declarationMapper.selectOne(new LambdaQueryWrapper<DeclarationEntity>()
                .eq(DeclarationEntity::getUserId, userId)
                .eq(DeclarationEntity::getDeclarationVersion, version)
                .orderByDesc(DeclarationEntity::getId)
                .last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public void save(UploadDeclaration declaration) {
        DeclarationEntity entity = new DeclarationEntity();
        entity.setUserId(declaration.getUserId());
        entity.setDeclarationVersion(declaration.getDeclarationVersion());
        entity.setAcceptedAt(declaration.getAcceptedAt());
        entity.setClientIp(declaration.getClientIp());
        entity.setUserAgent(declaration.getUserAgent());
        entity.setContentSource(declaration.getContentSource() == null
                ? null : declaration.getContentSource().name());
        declarationMapper.insert(entity);
        declaration.setId(entity.getId());
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 签署记录
     */
    private UploadDeclaration toDomain(DeclarationEntity entity) {
        UploadDeclaration declaration = new UploadDeclaration();
        declaration.setId(entity.getId());
        declaration.setUserId(entity.getUserId());
        declaration.setDeclarationVersion(entity.getDeclarationVersion());
        declaration.setAcceptedAt(entity.getAcceptedAt());
        declaration.setClientIp(entity.getClientIp());
        declaration.setUserAgent(entity.getUserAgent());
        declaration.setContentSource(CopyrightSource.parse(entity.getContentSource()));
        return declaration;
    }
}
