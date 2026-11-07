package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.marketing.domain.model.MarketingSend;
import com.bluewhale.crm.marketing.domain.model.MarketingSendQuery;
import com.bluewhale.crm.marketing.domain.model.MarketingSendStatus;
import com.bluewhale.crm.marketing.domain.model.SendChannel;
import com.bluewhale.crm.marketing.domain.repository.MarketingSendRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.MarketingSendEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.MarketingSendMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 批量触达任务仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MarketingSendRepositoryImpl implements MarketingSendRepository {

    private final MarketingSendMapper marketingSendMapper;

    @Override
    public List<MarketingSend> page(MarketingSendQuery query) {
        Page<MarketingSendEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return marketingSendMapper.selectPage(page, buildWrapper(query)).getRecords().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long count(MarketingSendQuery query) {
        return marketingSendMapper.selectCount(buildWrapper(query));
    }

    @Override
    public MarketingSend findById(Long id) {
        MarketingSendEntity entity = marketingSendMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(MarketingSend send) {
        MarketingSendEntity entity = toEntity(send);
        marketingSendMapper.insert(entity);
        send.setId(entity.getId());
        send.setCreateTime(entity.getCreateTime());
        send.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(MarketingSend send) {
        marketingSendMapper.updateById(toEntity(send));
    }

    @Override
    public long countBySendNoPrefix(String prefix) {
        return marketingSendMapper.selectCount(new LambdaQueryWrapper<MarketingSendEntity>()
                .likeRight(MarketingSendEntity::getSendNo, prefix));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<MarketingSendEntity> buildWrapper(MarketingSendQuery query) {
        LambdaQueryWrapper<MarketingSendEntity> wrapper = new LambdaQueryWrapper<MarketingSendEntity>()
                .eq(StringUtils.hasText(query.getStatus()), MarketingSendEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getChannel()), MarketingSendEntity::getChannel, query.getChannel())
                .eq(query.getCampaignId() != null, MarketingSendEntity::getCampaignId, query.getCampaignId())
                .eq(query.getScopeUserId() != null, MarketingSendEntity::getOwnerId, query.getScopeUserId())
                .orderByDesc(MarketingSendEntity::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(MarketingSendEntity::getSendNo, keyword)
                    .or().like(MarketingSendEntity::getSubject, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 触达任务
     */
    private MarketingSend toDomain(MarketingSendEntity entity) {
        MarketingSend send = new MarketingSend();
        send.setId(entity.getId());
        send.setTenantId(entity.getTenantId());
        send.setSendNo(entity.getSendNo());
        send.setCampaignId(entity.getCampaignId());
        send.setAudienceId(entity.getAudienceId());
        send.setChannel(entity.getChannel() == null ? null : SendChannel.from(entity.getChannel()));
        send.setTemplateCode(entity.getTemplateCode());
        send.setSubject(entity.getSubject());
        send.setContentRefDocId(entity.getContentRefDocId());
        send.setStatus(entity.getStatus() == null ? null : MarketingSendStatus.from(entity.getStatus()));
        send.setTotalCount(entity.getTotalCount());
        send.setSentCount(entity.getSentCount());
        send.setSkippedCount(entity.getSkippedCount());
        send.setFailedCount(entity.getFailedCount());
        send.setExecutedAt(entity.getExecutedAt());
        send.setLastError(entity.getLastError());
        send.setOwnerId(entity.getOwnerId());
        send.setDeptId(entity.getDeptId());
        send.setLockVersion(entity.getLockVersion());
        send.setCreateTime(entity.getCreateTime());
        send.setUpdateTime(entity.getUpdateTime());
        return send;
    }

    /**
     * 领域对象转实体。
     *
     * @param send 触达任务
     * @return 实体
     */
    private MarketingSendEntity toEntity(MarketingSend send) {
        MarketingSendEntity entity = new MarketingSendEntity();
        entity.setId(send.getId());
        entity.setTenantId(send.getTenantId());
        entity.setSendNo(send.getSendNo());
        entity.setCampaignId(send.getCampaignId());
        entity.setAudienceId(send.getAudienceId());
        entity.setChannel(send.getChannel() == null ? null : send.getChannel().name());
        entity.setTemplateCode(send.getTemplateCode());
        entity.setSubject(send.getSubject());
        entity.setContentRefDocId(send.getContentRefDocId());
        entity.setStatus(send.getStatus() == null ? null : send.getStatus().name());
        entity.setTotalCount(send.getTotalCount());
        entity.setSentCount(send.getSentCount());
        entity.setSkippedCount(send.getSkippedCount());
        entity.setFailedCount(send.getFailedCount());
        entity.setExecutedAt(send.getExecutedAt());
        entity.setLastError(send.getLastError());
        entity.setOwnerId(send.getOwnerId());
        entity.setDeptId(send.getDeptId());
        entity.setLockVersion(send.getLockVersion());
        return entity;
    }
}
