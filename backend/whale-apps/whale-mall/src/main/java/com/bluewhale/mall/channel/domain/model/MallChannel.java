package com.bluewhale.mall.channel.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 商城渠道。
 *
 * <p>这是「一套内核兼容三种商城形态」的配置面：{@code mode} 决定按哪种价目计价，
 * {@code settlementType} 决定怎么收钱。两个枚举的组合构成一个具体商城，
 * 新增形态不需要新服务、新表，甚至不需要新的订单逻辑。</p>
 *
 * <p>{@link #currencyType} <b>由结算方式推导，不收外部传值</b>：
 * 让运营分别选「结算方式」和「币种」，早晚会出现「在线支付 + 积分」这种
 * 无法解释的组合，然后在支付回调里炸掉。</p>
 *
 * @author BlueWhale
 */
@Data
public class MallChannel {

    /** 编码最大长度 */
    public static final int CODE_MAX_LENGTH = 64;

    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 主键 */
    private Long id;

    /** 渠道编码（租户内唯一） */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 形态 */
    private MallMode mode;

    /** 结算方式 */
    private SettlementType settlementType;

    /** 金额口径（由结算方式推导） */
    private CurrencyType currencyType;

    /** 状态 */
    private ChannelStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验渠道字段，并按结算方式推导币种口径。
     */
    public void validate() {
        if (channelCode == null || channelCode.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID, "渠道编码不能为空");
        }
        if (channelCode.trim().length() > CODE_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID,
                    "渠道编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        if (channelName == null || channelName.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID, "渠道名称不能为空");
        }
        if (channelName.trim().length() > NAME_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID,
                    "渠道名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (mode == null) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID, "必须指定商城形态");
        }
        if (settlementType == null) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID, "必须指定结算方式");
        }
        if (mode == MallMode.WELFARE && settlementType != SettlementType.POINTS) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID,
                    "员工福利商城必须使用积分结算");
        }
        if (mode != MallMode.WELFARE && settlementType == SettlementType.POINTS) {
            throw MallErrorCode.exception(MallErrorCode.CHANNEL_INVALID,
                    "只有员工福利商城可以使用积分结算");
        }
        channelCode = channelCode.trim().toUpperCase(Locale.ROOT);
        channelName = channelName.trim();
        currencyType = settlementType == SettlementType.POINTS ? CurrencyType.POINTS : CurrencyType.CASH;
        if (status == null) {
            status = ChannelStatus.ACTIVE;
        }
    }

    /**
     * 是否营业中（可下单）。
     *
     * @return 营业中返回 true
     */
    public boolean isActive() {
        return status == null || status == ChannelStatus.ACTIVE;
    }
}
