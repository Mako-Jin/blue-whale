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
}
