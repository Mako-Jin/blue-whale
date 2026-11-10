package com.bluewhale.mall.channel.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.channel.domain.model.ChannelQuery;
import com.bluewhale.mall.channel.domain.model.ChannelStatus;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.channel.domain.repository.ChannelRepository;
import com.bluewhale.mall.channel.infrastructure.persistence.entity.ChannelEntity;
import com.bluewhale.mall.channel.infrastructure.persistence.mapper.ChannelMapper;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 渠道仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ChannelRepositoryImpl implements ChannelRepository {

    private final ChannelMapper channelMapper;

    @Override
    public List<MallChannel> page(ChannelQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<ChannelEntity> page = channelMapper.selectPage(new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ChannelQuery query) {
        return channelMapper.selectCount(filter(query));
    }

    @Override
    public Optional<MallChannel> findById(Long id) {
        return Optional.ofNullable(channelMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsCode(String channelCode, Long excludeId) {
        if (!StringUtils.hasText(channelCode)) {
            return false;
        }
        return channelMapper.selectCount(new LambdaQueryWrapper<ChannelEntity>()
                .eq(ChannelEntity::getChannelCode, channelCode.trim().toUpperCase(Locale.ROOT))
                .ne(excludeId != null, ChannelEntity::getId, excludeId)) > 0;
    }

    @Override
    public Long save(MallChannel channel) {
        ChannelEntity entity = toEntity(channel);
        if (entity.getId() == null) {
            channelMapper.insert(entity);
        } else {
            channelMapper.updateById(entity);
        }
        channel.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void updateStatus(Long id, String status) {
        ChannelEntity entity = new ChannelEntity();
        entity.setId(id);
        entity.setStatus(status);
        channelMapper.updateById(entity);
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ChannelEntity> filter(ChannelQuery query) {
        LambdaQueryWrapper<ChannelEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(ChannelEntity::getChannelCode, keyword)
                    .or().like(ChannelEntity::getChannelName, keyword));
        }
        if (query != null && StringUtils.hasText(query.getMode())) {
            wrapper.eq(ChannelEntity::getMode, query.getMode().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ChannelEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByDesc(ChannelEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MallChannel toDomain(ChannelEntity entity) {
        MallChannel channel = new MallChannel();
        channel.setId(entity.getId());
        channel.setChannelCode(entity.getChannelCode());
        channel.setChannelName(entity.getChannelName());
        channel.setMode(MallMode.parse(entity.getMode()));
        channel.setSettlementType(SettlementType.parse(entity.getSettlementType()));
        channel.setCurrencyType(CurrencyType.parse(entity.getCurrencyType()));
        channel.setFreightTemplateId(entity.getFreightTemplateId());
        channel.setStatus(ChannelStatus.parse(entity.getStatus()));
        channel.setRemark(entity.getRemark());
        channel.setCreateTime(entity.getCreateTime());
        channel.setUpdateTime(entity.getUpdateTime());
        return channel;
    }

    /**
     * 领域模型转实体。
     *
     * @param channel 领域模型
     * @return 实体
     */
    private ChannelEntity toEntity(MallChannel channel) {
        ChannelEntity entity = new ChannelEntity();
        entity.setId(channel.getId());
        entity.setChannelCode(channel.getChannelCode());
        entity.setChannelName(channel.getChannelName());
        entity.setMode(channel.getMode() == null ? null : channel.getMode().name());
        entity.setSettlementType(channel.getSettlementType() == null ? null : channel.getSettlementType().name());
        entity.setCurrencyType(channel.getCurrencyType() == null ? null : channel.getCurrencyType().name());
        entity.setFreightTemplateId(channel.getFreightTemplateId());
        entity.setStatus(channel.getStatus() == null ? ChannelStatus.ACTIVE.name() : channel.getStatus().name());
        entity.setRemark(channel.getRemark());
        return entity;
    }
}
