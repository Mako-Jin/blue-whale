package com.bluewhale.party.integration.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.integration.domain.model.NotifyLog;
import com.bluewhale.party.integration.domain.model.NotifyLogQuery;
import com.bluewhale.party.integration.domain.model.NotifyScene;
import com.bluewhale.party.integration.domain.model.NotifyStatus;
import com.bluewhale.party.integration.domain.repository.NotifyLogRepository;
import com.bluewhale.party.integration.infrastructure.persistence.entity.NotifyLogEntity;
import com.bluewhale.party.integration.infrastructure.persistence.mapper.NotifyLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 通知留痕仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class NotifyLogRepositoryImpl implements NotifyLogRepository {

    private final NotifyLogMapper notifyLogMapper;

    @Override
    public Optional<NotifyLog> findByKeyAndUser(String idempotencyKey, Long userId) {
        return Optional.ofNullable(notifyLogMapper.selectOne(new LambdaQueryWrapper<NotifyLogEntity>()
                        .eq(NotifyLogEntity::getIdempotencyKey, idempotencyKey)
                        .eq(NotifyLogEntity::getUserId, userId)
                        .last("LIMIT 1")))
                .map(this::toDomain);
    }

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
    public Long save(NotifyLog log) {
        NotifyLogEntity entity = toEntity(log);
        if (entity.getId() == null) {
            notifyLogMapper.insert(entity);
        } else {
            notifyLogMapper.updateById(entity);
        }
        log.setId(entity.getId());
        return entity.getId();
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<NotifyLogEntity> filter(NotifyLogQuery query) {
        LambdaQueryWrapper<NotifyLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(NotifyLogEntity::getId);
        }
        if (StringUtils.hasText(query.getScene())) {
            wrapper.eq(NotifyLogEntity::getScene, query.getScene().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (query.getUserId() != null) {
            wrapper.eq(NotifyLogEntity::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getSendStatus())) {
            wrapper.eq(NotifyLogEntity::getSendStatus,
                    query.getSendStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (StringUtils.hasText(query.getIdempotencyKey())) {
            wrapper.eq(NotifyLogEntity::getIdempotencyKey, query.getIdempotencyKey().trim());
        }
        return wrapper.orderByDesc(NotifyLogEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
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
     * 领域模型转实体。
     *
     * @param log 领域模型
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
        entity.setChannel(log.getChannel());
        entity.setSendStatus(log.getSendStatus() == null ? null : log.getSendStatus().name());
        entity.setFailReason(log.getFailReason());
        entity.setSentAt(log.getSentAt());
        return entity;
    }
}
