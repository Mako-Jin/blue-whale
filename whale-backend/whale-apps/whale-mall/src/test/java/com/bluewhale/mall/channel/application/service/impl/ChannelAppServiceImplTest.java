package com.bluewhale.mall.channel.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.channel.application.command.ChannelSaveCommand;
import com.bluewhale.mall.channel.application.view.ChannelView;
import com.bluewhale.mall.channel.domain.model.ChannelQuery;
import com.bluewhale.mall.channel.domain.model.ChannelStatus;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.channel.domain.repository.ChannelRepository;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import com.bluewhale.mall.config.MallProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 渠道应用服务：编码查重、模式解析、启停。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ChannelAppServiceImplTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private MallProperties properties;

    @InjectMocks
    private ChannelAppServiceImpl service;

    private MallActor actor;

    @BeforeEach
    void setUp() {
        actor = new MallActor(1L, "admin", Set.of("mall_admin"), Set.of());
        lenient().when(properties.getPageLimit()).thenReturn(100);
    }

    @Test
    @DisplayName("新建福利渠道，币种推导为积分")
    void createWelfareChannel() {
        when(channelRepository.existsCode("WF-1", null)).thenReturn(false);
        when(channelRepository.save(any(MallChannel.class))).thenReturn(3L);

        ChannelView view = service.create(command("WF-1", "WELFARE", "POINTS"), actor);

        assertThat(view.getCurrencyType()).isEqualTo(CurrencyType.POINTS.name());
        assertThat(view.getStatus()).isEqualTo(ChannelStatus.ACTIVE.name());
    }

    @Test
    @DisplayName("渠道编码重复被拒绝")
    void createRejectsDuplicateCode() {
        when(channelRepository.existsCode("WF-1", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(command("WF-1", "WELFARE", "POINTS"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CHANNEL_CODE_CONFLICT));
        verify(channelRepository, never()).save(any(MallChannel.class));
    }

    @Test
    @DisplayName("未知形态被拒绝，不静默回落零售")
    void createRejectsUnknownMode() {
        assertThatThrownBy(() -> service.create(command("X-1", "WHOLESALE", "ONLINE"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CHANNEL_INVALID));
    }

    @Test
    @DisplayName("编辑不存在的渠道")
    void updateMissingChannel() {
        when(channelRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(9L, command("R-1", "RETAIL", "ONLINE"), actor))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getCode())
                        .isEqualTo(MallErrorCode.CHANNEL_NOT_FOUND));
    }

    @Test
    @DisplayName("已启用的渠道重复启用是空操作")
    void changeStatusIdempotent() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel()));
        service.changeStatus(1L, "ACTIVE", actor);
        verify(channelRepository, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("停用渠道")
    void disableChannel() {
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel()));
        service.changeStatus(1L, "DISABLED", actor);
        verify(channelRepository).updateStatus(1L, "DISABLED");
    }

    @Test
    @DisplayName("分页返回渠道列表")
    void page() {
        when(channelRepository.page(any(ChannelQuery.class), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(channel()));
        when(channelRepository.count(any())).thenReturn(1L);

        PageResult<ChannelView> page = service.page(new ChannelQuery(), 1L, 20L, actor);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords().get(0).getChannelCode()).isEqualTo("RETAIL-1");
    }

    private ChannelSaveCommand command(String code, String mode, String settlement) {
        ChannelSaveCommand command = new ChannelSaveCommand();
        command.setChannelCode(code);
        command.setChannelName("测试渠道");
        command.setMode(mode);
        command.setSettlementType(settlement);
        return command;
    }

    private MallChannel channel() {
        MallChannel channel = new MallChannel();
        channel.setId(1L);
        channel.setChannelCode("RETAIL-1");
        channel.setChannelName("官方商城");
        channel.setMode(MallMode.RETAIL);
        channel.setSettlementType(SettlementType.ONLINE);
        channel.setCurrencyType(CurrencyType.CASH);
        channel.setStatus(ChannelStatus.ACTIVE);
        return channel;
    }
}
