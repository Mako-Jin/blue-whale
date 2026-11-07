package com.bluewhale.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商城应用运行参数（配置前缀 {@code whale.mall}）。
 *
 * <p>这里只放「运维需要在不改代码的情况下调整」的阈值。业务规则（例如价格缺失时的
 * 回落策略）一律写在代码里：阈值调错了顶多是体验问题，规则被配置改掉就是账目问题。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.mall")
public class MallProperties {

    /** 下单是否强制要求幂等键（生产必须开启；关闭仅用于本地压测） */
    private boolean idempotencyRequired = true;

    /** 购物车单个 SKU 数量上限 */
    private int cartMaxQuantity = 999;

    /** 单笔订单最大明细行数 */
    private int orderMaxLines = 50;

    /** 待支付订单自动关闭时长（分钟，0 表示不自动关闭） */
    private int orderExpireMinutes = 30;

    /** 列表分页单页上限 */
    private int pageLimit = 100;
}
