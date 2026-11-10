package com.bluewhale.mall.interfaces.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 商城门面信息（供前端初始化下拉项，避免把枚举硬编码在前端）。
 *
 * @author BlueWhale
 */
@Data
public class MallMetaView {

    /** 服务名 */
    private String service;

    /** 版本标识 */
    private String version;

    /** 支持的商城形态 */
    private List<String> modes = new ArrayList<>();

    /** 支持的结算方式 */
    private List<String> settlementTypes = new ArrayList<>();

    /** 支持的金额口径 */
    private List<String> currencyTypes = new ArrayList<>();

    /** 支持的价目类型 */
    private List<String> priceTypes = new ArrayList<>();

    /** 订单状态 */
    private List<String> orderStatuses = new ArrayList<>();
}
