package com.bluewhale.pay.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 支付渠道启动守卫。
 *
 * <p>当前支付/退款的渠道调用仍与本地数据库事务绑定：一旦渠道已扣款而本地事务回滚，
 * 会出现「资金已动、账务未动」的不一致。MOCK 模式不产生真实资金动作，因此是安全的；
 * 关闭 mock 意味着要接真实资金，必须先把渠道调用移出本地事务并补齐补偿/对账流程。
 * 在改造完成前，{@code whale.pay.mock=false} 直接拒绝启动，避免误上线。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class PayChannelStartupGuard implements InitializingBean {

    private final PayChannelProperties properties;

    /**
     * 构造启动守卫。
     *
     * @param properties 渠道配置
     */
    public PayChannelStartupGuard(PayChannelProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动校验：非 mock 模式直接失败。
     */
    @Override
    public void afterPropertiesSet() {
        if (properties.isMock()) {
            log.info("支付渠道运行于 MOCK 模式：不发起真实资金请求");
            return;
        }
        throw new IllegalStateException("whale.pay.mock=false 已禁止启动：渠道调用仍在本地事务内，"
                + "真实扣款后若本地事务回滚会导致资金与账务不一致。"
                + "请先完成「渠道调用移出事务 + 补偿/对账」改造，再移除 PayChannelStartupGuard 的拦截。");
    }
}
