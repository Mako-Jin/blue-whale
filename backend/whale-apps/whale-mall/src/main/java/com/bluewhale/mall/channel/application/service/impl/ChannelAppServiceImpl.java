package com.bluewhale.mall.channel.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.channel.application.command.ChannelSaveCommand;
import com.bluewhale.mall.channel.application.service.ChannelAppService;
import com.bluewhale.mall.channel.application.view.ChannelView;
import com.bluewhale.mall.channel.domain.model.ChannelQuery;
import com.bluewhale.mall.channel.domain.model.ChannelStatus;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.channel.domain.repository.ChannelRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import com.bluewhale.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 渠道应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ChannelAppServiceImpl implements ChannelAppService {

    private final ChannelRepository channelRepository;

    private final MallProperties properties;

    @Override
    public PageResult<ChannelView> page(ChannelQuery query, Long current, Long size, MallActor actor) {
        long pageSize = size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
        long pageNo = current == null || current < 1 ? 1L : current;
        List<MallChannel> channels = channelRepository.page(query == null ? new ChannelQuery() : query,
                (pageNo - 1L) * pageSize, (int) pageSize);
        List<ChannelView> views = new ArrayList<>(channels.size());
        for (MallChannel channel : channels) {
            views.add(toView(channel));
        }
        return PageResult.of(views, channelRepository.count(query), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelView create(ChannelSaveCommand command, MallActor actor) {
        actor.requireUserId();
        MallChannel channel = new MallChannel();
        apply(channel, command);
        channel.validate();
        if (channelRepository.existsCode(channel.getChannelCode(), null)) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_CODE_CONFLICT);
        }
        channelRepository.save(channel);
        return toView(channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChannelView update(Long id, ChannelSaveCommand command, MallActor actor) {
        actor.requireUserId();
        MallChannel channel = requireChannel(id);
        apply(channel, command);
        channel.validate();
        if (channelRepository.existsCode(channel.getChannelCode(), id)) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_CODE_CONFLICT);
        }
        channelRepository.save(channel);
        return toView(channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status, MallActor actor) {
        actor.requireUserId();
        MallChannel channel = requireChannel(id);
        ChannelStatus target = ChannelStatus.parse(status);
        if (target == ChannelStatus.ACTIVE && channel.getStatus() == ChannelStatus.ACTIVE) {
            return;
        }
        channelRepository.updateStatus(id, target.name());
    }

    /**
     * 将命令写入渠道（形态与结算方式必须能被解析，否则拒绝而不是静默回落）。
     *
     * @param channel 渠道
     * @param command 命令
     */
    private void apply(MallChannel channel, ChannelSaveCommand command) {
        channel.setChannelCode(command.getChannelCode());
        channel.setChannelName(command.getChannelName());
        MallMode mode = MallMode.parse(command.getMode());
        if (mode == null) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID,
                    "未知商城形态：" + command.getMode());
        }
        SettlementType settlement = SettlementType.parse(command.getSettlementType());
        if (settlement == null) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID,
                    "未知结算方式：" + command.getSettlementType());
        }
        channel.setMode(mode);
        channel.setSettlementType(settlement);
        channel.setRemark(command.getRemark());
    }

    /**
     * 查询渠道，不存在即失败。
     *
     * @param id 渠道 ID
     * @return 渠道
     */
    private MallChannel requireChannel(Long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.CHANNEL_NOT_FOUND));
    }

    /**
     * 领域模型转视图。
     *
     * @param channel 渠道
     * @return 视图
     */
    private ChannelView toView(MallChannel channel) {
        ChannelView view = new ChannelView();
        view.setId(channel.getId());
        view.setChannelCode(channel.getChannelCode());
        view.setChannelName(channel.getChannelName());
        view.setMode(channel.getMode() == null ? null : channel.getMode().name());
        view.setSettlementType(channel.getSettlementType() == null ? null : channel.getSettlementType().name());
        view.setCurrencyType(channel.getCurrencyType() == null ? null : channel.getCurrencyType().name());
        view.setStatus(channel.getStatus() == null ? ChannelStatus.ACTIVE.name() : channel.getStatus().name());
        view.setRemark(channel.getRemark());
        view.setCreateTime(channel.getCreateTime());
        return view;
    }
}
