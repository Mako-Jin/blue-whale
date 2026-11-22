package com.bluewhale.mall.order.application.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 下单试算命令。
 *
 * <p>{@code itemIds} 为空表示「该渠道购物车中所有已选中的条目」；
 * 显式传 ID 时只算这几条（用于「立即购买」绕过购物车选中态的路径）。</p>
 *
 * @author BlueWhale
 */
@Data
public class OrderPreviewCommand {

    /** 渠道 ID */
    private Long channelId;

    /** 购物车条目 ID 列表（空表示已选中的全部） */
    private List<Long> itemIds = new ArrayList<>();

    /** 预选的用户券 ID（可空；传了才按券计价，用于「选券后看实付」；单券兼容口径） */
    private Long couponId;

    /** 预选的叠加用户券 ID 列表（可空，最多 2 张且折扣券最多 1 张） */
    private List<Long> couponIds;

    /** 收货区域（省/直辖市名，可空；区域运费模板按此计收，未传按兜底运费） */
    @Size(max = 64, message = "{validation.mall.order-preview.region-size}")
    private String region;
}
