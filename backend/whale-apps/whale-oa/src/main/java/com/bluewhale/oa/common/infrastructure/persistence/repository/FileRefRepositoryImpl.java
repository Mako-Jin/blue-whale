package com.bluewhale.oa.common.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.oa.common.domain.model.FileRef;
import com.bluewhale.oa.common.domain.repository.FileRefRepository;
import com.bluewhale.oa.common.infrastructure.persistence.entity.FileRefEntity;
import com.bluewhale.oa.common.infrastructure.persistence.mapper.FileRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文件引用仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class FileRefRepositoryImpl implements FileRefRepository {

    private final FileRefMapper fileRefMapper;

    @Override
    public List<FileRef> listByBiz(String bizType, Long bizId) {
        return fileRefMapper.selectList(new LambdaQueryWrapper<FileRefEntity>()
                        .eq(FileRefEntity::getBizType, bizType)
                        .eq(FileRefEntity::getBizId, bizId)
                        .orderByAsc(FileRefEntity::getCreateTime))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<FileRef> findByBizAndFile(String bizType, Long bizId, Long fileId) {
        return Optional.ofNullable(fileRefMapper.selectOne(new LambdaQueryWrapper<FileRefEntity>()
                .eq(FileRefEntity::getBizType, bizType)
                .eq(FileRefEntity::getBizId, bizId)
                .eq(FileRefEntity::getFileId, fileId)
                .last("limit 1"))).map(this::toDomain);
    }

    @Override
    public Optional<FileRef> findById(Long id) {
        return Optional.ofNullable(fileRefMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<FileRef> insertIfAbsent(FileRef fileRef) {
        Optional<FileRef> existing = findByBizAndFile(fileRef.getBizType(), fileRef.getBizId(), fileRef.getFileId());
        if (existing.isPresent()) {
            return existing;
        }
        try {
            FileRefEntity entity = new FileRefEntity();
            fill(entity, fileRef);
            fileRefMapper.insert(entity);
            fileRef.setId(entity.getId());
            return Optional.of(fileRef);
        } catch (DuplicateKeyException exception) {
            return findByBizAndFile(fileRef.getBizType(), fileRef.getBizId(), fileRef.getFileId());
        }
    }

    @Override
    public void deleteById(Long id) {
        fileRefMapper.deleteById(id);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private FileRef toDomain(FileRefEntity entity) {
        FileRef fileRef = new FileRef();
        fileRef.setId(entity.getId());
        fileRef.setBizType(entity.getBizType());
        fileRef.setBizId(entity.getBizId());
        fileRef.setFileId(entity.getFileId());
        fileRef.setFileName(entity.getFileName());
        fileRef.setFileSize(entity.getFileSize());
        fileRef.setContentType(entity.getContentType());
        fileRef.setUploadedBy(entity.getUploadedBy());
        fileRef.setPurpose(entity.getPurpose());
        fileRef.setCreateTime(entity.getCreateTime());
        return fileRef;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param fileRef 领域模型
     */
    private void fill(FileRefEntity entity, FileRef fileRef) {
        entity.setBizType(fileRef.getBizType());
        entity.setBizId(fileRef.getBizId());
        entity.setFileId(fileRef.getFileId());
        entity.setFileName(fileRef.getFileName());
        entity.setFileSize(fileRef.getFileSize());
        entity.setContentType(fileRef.getContentType());
        entity.setUploadedBy(fileRef.getUploadedBy());
        entity.setPurpose(fileRef.getPurpose());
    }
}
