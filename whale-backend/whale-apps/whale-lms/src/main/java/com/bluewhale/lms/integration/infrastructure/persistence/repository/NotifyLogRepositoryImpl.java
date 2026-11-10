package com.bluewhale.lms.integration.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.integration.domain.model.NotifyLog;
import com.bluewhale.lms.integration.domain.model.NotifyLogQuery;
import com.bluewhale.lms.integration.domain.model.NotifyScene;
import com.bluewhale.lms.integration.domain.model.NotifyStatus;
import com.bluewhale.lms.integration.domain.repository.NotifyLogRepository;
import com.bluewhale.lms.integration.infrastructure.persistence.entity.NotifyLogEntity;
import com.bluewhale.lms.integration.infrastructure.persistence.mapper.NotifyLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 通知投递留痕仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class NotifyLogRepositoryImpl implements NotifyLogRepository {

    private final NotifyLogMapper notifyLogMapper;

    @Override
    public List<NotifyLog> page(NotifyLogQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<NotifyLogEntity> page = notifyLogMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(NotifyLogQuery query) {
        return notifyLogMapper.selectCount(filter(query));
    }

    @Override
    public Optional<NotifyLog> findByKeyAndUser(String idempotencyKey, Long userId) {
        if (!StringUtils.hasText(idempotencyKey) || userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(notifyLogMapper.selectOne(new LambdaQueryWrapper<NotifyLogEntity>()
                        .eq(NotifyLogEntity::getIdempotencyKey, idempotencyKey)
                        .eq(NotifyLogEntity::getUserId, userId)))
                .map(this::toDomain);
    }

    @Override
    public Long save(NotifyLog log) {
        NotifyLogEntity entity = toEntity(log);
        if (log.getId() == null) {
            notifyLogMapper.insert(entity);
        } else {
            notifyLogMapper.updateById(entity);
        }
        log.setId(entity.getId());
        log.setCreateTime(entity.getCreateTime());
        log.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public long countSentByKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return 0L;
        }
        return notifyLogMapper.selectCount(new LambdaQueryWrapper<NotifyLogEntity>()
                .eq(NotifyLogEntity::getIdempotencyKey, idempotencyKey)
                .eq(NotifyLogEntity::getSendStatus, NotifyStatus.SENT.name()));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<NotifyLogEntity> filter(NotifyLogQuery query) {
        return new LambdaQueryWrapper<NotifyLogEntity>()
                .eq(StringUtils.hasText(query.getScene()), NotifyLogEntity::getScene,
                        query.getScene() == null ? null : query.getScene().trim().toUpperCase())
                .eq(query.getUserId() != null, NotifyLogEntity::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getSendStatus()), NotifyLogEntity::getSendStatus,
                        query.getSendStatus() == null ? null : query.getSendStatus().trim().toUpperCase())
                .eq(StringUtils.hasText(query.getIdempotencyKey()), NotifyLogEntity::getIdempotencyKey,
                        query.getIdempotencyKey() == null ? null : query.getIdempotencyKey().trim())
                .orderByDesc(NotifyLogEntity::getSentAt);
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 日志
     */
    private NotifyLog toDomain(NotifyLogEntity entity) {
        NotifyLog log = new NotifyLog();
        log.setId(entity.getId());
        log.setScene(NotifyScene.parse(entity.getScene()));
        log.setIdempotencyKey(entity.getIdempotencyKey());
        log.setBizType(entity.getBizType());
        log.setBizId(entity.getBizId());
        log.setUserId(entity.getUserId());
        log.setTemplateCode(entity.getTemplateCode());
        log.setTitle(entity.getTitle());
        log.setChannel(entity.getChannel());
        log.setSendStatus(NotifyStatus.parse(entity.getSendStatus()));
        log.setFailReason(entity.getFailReason());
        log.setSentAt(entity.getSentAt());
        log.setCreateTime(entity.getCreateTime());
        log.setUpdateTime(entity.getUpdateTime());
        return log;
    }

    /**
     * 领域转实体。
     *
     * @param log 日志
     * @return 实体
     */
    private NotifyLogEntity toEntity(NotifyLog log) {
        NotifyLogEntity entity = new NotifyLogEntity();
        entity.setId(log.getId());
        entity.setScene(log.getScene() == null ? null : log.getScene().name());
        entity.setIdempotencyKey(log.getIdempotencyKey());
        entity.setBizType(log.getBizType());
        entity.setBizId(log.getBizId());
        entity.setUserId(log.getUserId());
        entity.setTemplateCode(log.getTemplateCode());
        entity.setTitle(log.getTitle());
        entity.setChannel(log.getChannel() == null ? "INAPP" : log.getChannel());
        entity.setSendStatus(log.getSendStatus() == null ? NotifyStatus.FAILED.name() : log.getSendStatus().name());
        entity.setFailReason(log.getFailReason());
        entity.setSentAt(log.getSentAt());
        entity.setCreateTime(log.getCreateTime());
        entity.setUpdateTime(log.getUpdateTime());
        return entity;
    }
}
