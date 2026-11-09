package com.bluewhale.mall.order.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 下单命令。
 *
 * @author BlueWhale
 */
@Data
public class OrderCreateCommand {

    /** 渠道 ID */
    @NotNull(message = "必须指定商城渠道")
    private Long channelId;

    /** 购物车条目 ID 列表（空表示已选中的全部） */
    private List<Long> itemIds = new ArrayList<>();

    /** 买家备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;

    /**
     * 使用的用户券 ID（可空，兼容单券口径）。
     *
     * <p>只传券 ID，不传金额：金额由服务端按券规则重算。信前端传的优惠额，
     * 等于把优惠额度开放给浏览器。</p>
     */
    private Long couponId;

    /**
     * 叠加使用的用户券 ID 列表（可空）。
     *
     * <p>一单最多 2 张、折扣券最多 1 张，上限由服务端校验。
     * 本字段为空且 {@link #couponId} 非空时按单券处理（老前端兼容）。</p>
     */
    private List<Long> couponIds;

    /** 收货区域（省/直辖市名，可空；区域运费模板按此计收，未传按兜底运费） */
    @Size(max = 64, message = "收货区域不得超过 64 字符")
    private String region;
}
