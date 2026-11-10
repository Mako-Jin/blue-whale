package com.bluewhale.mall.channel.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mall.channel.domain.model.MallChannel;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 渠道形态 × 结算方式组合规则测试。
 *
 * @author BlueWhale
 */
class MallChannelModeTest {

    /**
     * 福利商城 + 积分结算：合法，币种自动推导为积分。
     */
    @Test
    @DisplayName("福利渠道使用积分结算，币种自动推导")
    void welfareChannelUsesPoints() {
        MallChannel channel = channel(MallMode.WELFARE, SettlementType.POINTS);
        channel.validate();
        assertThat(channel.getCurrencyType()).isEqualTo(CurrencyType.POINTS);
        assertThat(channel.getChannelCode()).isEqualTo("WF-1");
        assertThat(channel.getStatus().name()).isEqualTo("ACTIVE");
    }

    /**
     * 零售商城 + 在线支付：合法，币种为元。
     */
    @Test
    @DisplayName("零售渠道使用在线支付，币种为元")
    void retailChannelUsesCash() {
        MallChannel channel = channel(MallMode.RETAIL, SettlementType.ONLINE);
        channel.validate();
        assertThat(channel.getCurrencyType()).isEqualTo(CurrencyType.CASH);
    }

    /**
     * 福利商城却用在线支付：拒绝。
     */
    @Test
    @DisplayName("福利商城不允许在线支付")
    void welfareWithOnlineRejected() {
        MallChannel channel = channel(MallMode.WELFARE, SettlementType.ONLINE);
        assertThatThrownBy(channel::validate).isInstanceOf(BizException.class);
    }

    /**
     * 零售商城却用积分结算：拒绝。
     */
    @Test
    @DisplayName("非福利商城不允许积分结算")
    void retailWithPointsRejected() {
        MallChannel channel = channel(MallMode.RETAIL, SettlementType.POINTS);
        assertThatThrownBy(channel::validate).isInstanceOf(BizException.class);
    }

    /**
     * 形态与结算方式缺失：拒绝（不静默回落零售）。
     */
    @Test
    @DisplayName("形态或结算方式缺失被拒绝")
    void missingModeOrSettlementRejected() {
        MallChannel noMode = channel(null, SettlementType.ONLINE);
        assertThatThrownBy(noMode::validate).isInstanceOf(BizException.class);
        MallChannel noSettlement = channel(MallMode.RETAIL, null);
        assertThatThrownBy(noSettlement::validate).isInstanceOf(BizException.class);
    }

    /**
     * 构造渠道。
     *
     * @param mode 形态
     * @param settlement 结算方式
     * @return 渠道
     */
    private MallChannel channel(MallMode mode, SettlementType settlement) {
        MallChannel channel = new MallChannel();
        channel.setChannelCode("wf-1");
        channel.setChannelName("员工福利商城");
        channel.setMode(mode);
        channel.setSettlementType(settlement);
        return channel;
    }
}
