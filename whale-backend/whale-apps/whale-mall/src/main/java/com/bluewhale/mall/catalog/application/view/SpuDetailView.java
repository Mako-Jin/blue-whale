package com.bluewhale.mall.catalog.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品详情视图（含轮播图、详情、SKU 与价目、库存）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpuDetailView extends SpuView {

    /** 轮播图 */
    private List<String> images = new ArrayList<>();

    /** 图文详情 */
    private String detail;

    /** 备注 */
    private String remark;

    /** 规格列表 */
    private List<SkuView> skus = new ArrayList<>();
}
