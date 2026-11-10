package com.bluewhale.mall.cart.application.command;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车选中态批量修改命令。
 *
 * @author BlueWhale
 */
@Data
public class CartSelectCommand {

    /** 渠道 ID */
    private Long channelId;

    /** 条目 ID 列表；为空表示全选/全不选该渠道购物车 */
    private List<Long> itemIds = new ArrayList<>();

    /** 目标选中态 */
    private Boolean selected;
}
