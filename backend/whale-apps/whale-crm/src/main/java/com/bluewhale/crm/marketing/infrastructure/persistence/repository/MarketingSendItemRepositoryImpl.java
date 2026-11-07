package com.bluewhale.crm.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.marketing.domain.model.MarketingSendItem;
import com.bluewhale.crm.marketing.domain.model.SendChannel;
import com.bluewhale.crm.marketing.domain.model.SendItemStatus;
import com.bluewhale.crm.marketing.domain.repository.MarketingSendItemRepository;
import com.bluewhale.crm.marketing.infrastructure.persistence.entity.MarketingSendItemEntity;
import com.bluewhale.crm.marketing.infrastructure.persistence.mapper.MarketingSendItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 批量触达明细仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MarketingSendItemRepositoryImpl implements MarketingSendItemRepository {

    private final MarketingSendItemMapper marketingSendItemMapper;

    @Override
    public void insertBatch(List<MarketingSendItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MarketingSendItem item : items) {
            MarketingSendItemEntity entity = toEntity(item);
            marketingSendItemMapper.insert(entity);
            item.setId(entity.getId());
        }
    }

    @Override
    public List<MarketingSendItem> findBySend(Long sendId) {
        return marketingSendItemMapper.selectList(new LambdaQueryWrapper<MarketingSendItemEntity>()
                        .eq(MarketingSendItemEntity::getSendId, sendId)
                        .orderByAsc(MarketingSendItemEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void update(MarketingSendItem item) {
        marketingSendItemMapper.updateById(toEntity(item));
    }

    @Override
    public boolean existsByCampaignContactChannel(Long campaignId, Long contactId, SendChannel channel) {
        if (campaignId == null || contactId == null || channel == null) {
            return false;
        }
        return marketingSendItemMapper.selectCount(new LambdaQueryWrapper<MarketingSendItemEntity>()
                .eq(MarketingSendItemEntity::getCampaignId, campaignId)
                .eq(MarketingSendItemEntity::getContactId, contactId)
                .eq(MarketingSendItemEntity::getChannel, channel.name())
                .eq(MarketingSendItemEntity::getStatus, SendItemStatus.SENT.name())) > 0;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 明细
     */
    private MarketingSendItem toDomain(MarketingSendItemEntity entity) {
        MarketingSendItem item = new MarketingSendItem();
        item.setId(entity.getId());
        item.setTenantId(entity.getTenantId());
        item.setSendId(entity.getSendId());
        item.setCampaignId(entity.getCampaignId());
        item.setCustomerId(entity.getCustomerId());
        item.setContactId(entity.getContactId());
        item.setContactName(entity.getContactName());
        item.setRecipient(entity.getRecipient());
        item.setChannel(entity.getChannel() == null ? null : SendChannel.from(entity.getChannel()));
        item.setStatus(entity.getStatus() == null ? null : SendItemStatus.from(entity.getStatus()));
        item.setError(entity.getError());
        item.setSentAt(entity.getSentAt());
        item.setCreateTime(entity.getCreateTime());
        item.setUpdateTime(entity.getUpdateTime());
        return item;
    }

    /**
     * 领域对象转实体。
     *
     * @param item 明细
     * @return 实体
     */
    private MarketingSendItemEntity toEntity(MarketingSendItem item) {
        MarketingSendItemEntity entity = new MarketingSendItemEntity();
        entity.setId(item.getId());
        entity.setTenantId(item.getTenantId());
        entity.setSendId(item.getSendId());
        entity.setCampaignId(item.getCampaignId());
        entity.setCustomerId(item.getCustomerId());
        entity.setContactId(item.getContactId());
        entity.setContactName(item.getContactName());
        entity.setRecipient(item.getRecipient());
        entity.setChannel(item.getChannel() == null ? null : item.getChannel().name());
        entity.setStatus(item.getStatus() == null ? null : item.getStatus().name());
        entity.setError(item.getError());
        entity.setSentAt(item.getSentAt());
        return entity;
    }
}
