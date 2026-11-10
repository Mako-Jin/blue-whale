package com.bluewhale.mall.channel.domain.model;

import lombok.Data;

/**
 * 渠道查询条件。
 *
 * @author BlueWhale
 */
@Data
public class ChannelQuery {

    /** 关键字（编码或名称） */
    private String keyword;

    /** 形态 */
    private String mode;

    /** 状态 */
    private String status;
}
