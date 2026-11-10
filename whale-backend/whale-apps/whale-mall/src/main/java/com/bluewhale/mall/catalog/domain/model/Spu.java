package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 商品（SPU）。
 *
 * <p>SPU 与 SKU 是一个聚合：保存商品时 SKU 与价目一起提交、一起生效。
 * 拆成两次请求会出现「商品已上架但还没有价格」的中间态，买家点进去就是报错。</p>
 *
 * @author BlueWhale
 */
@Data
public class Spu {

    /** 编码最大长度 */
    public static final int CODE_MAX_LENGTH = 64;

    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 图片地址最大长度 */
    public static final int IMAGE_MAX_LENGTH = 512;

    /** 单个商品最多 SKU 数 */
    public static final int MAX_SKU_COUNT = 200;

    /** 主键 */
    private Long id;

    /** 商品编码（租户内唯一） */
    private String spuCode;

    /** 商品名称 */
    private String spuName;

    /** 所属类目 ID */
    private Long categoryId;

    /** 品牌 */
    private String brand;

    /** 主图 */
    private String mainImage;

    /** 轮播图 */
    private List<String> images = new ArrayList<>();

    /** 图文详情 */
    private String detail;

    /** 状态 */
    private SpuStatus status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 规格列表 */
    private List<Sku> skus = new ArrayList<>();

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验商品字段与 SKU 列表。
     */
    public void validate() {
        validateBase();
        validateSkus();
        spuCode = spuCode.trim().toUpperCase(Locale.ROOT);
        spuName = spuName.trim();
        if (status == null) {
            status = SpuStatus.DRAFT;
        }
    }

    /**
     * 校验商品自身字段。
     */
    private void validateBase() {
        if (spuCode == null || spuCode.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID, "商品编码不能为空");
        }
        if (spuCode.trim().length() > CODE_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID,
                    "商品编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        if (spuName == null || spuName.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID, "商品名称不能为空");
        }
        if (spuName.trim().length() > NAME_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID,
                    "商品名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (categoryId == null || categoryId <= 0) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID, "必须选择所属类目");
        }
        if (mainImage != null && mainImage.length() > IMAGE_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID, "主图地址过长");
        }
    }

    /**
     * 校验 SKU 列表与价目。
     */
    private void validateSkus() {
        if (skus == null || skus.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID, "至少需要一个规格（SKU）");
        }
        if (skus.size() > MAX_SKU_COUNT) {
            throw MallErrorCode.exception(MallErrorCode.PRODUCT_INVALID,
                    "单个商品的规格数不得超过 " + MAX_SKU_COUNT);
        }
        for (Sku sku : skus) {
            sku.validate();
            if (sku.getPrices() == null || sku.getPrices().isEmpty()) {
                throw MallErrorCode.exception(MallErrorCode.PRICE_MISSING,
                        "SKU " + sku.getSkuCode() + " 至少需要配置一个价目");
            }
            for (SkuPrice price : sku.getPrices()) {
                if (price.getPriceType() == null) {
                    throw MallErrorCode.exception(MallErrorCode.PRICE_INVALID, "价目类型不能为空");
                }
                price.setPrice(price.getPriceType().normalize(price.getPrice()));
            }
        }
    }

    /**
     * 上架。
     */
    public void publish() {
        status.assertTransitionTo(SpuStatus.ON_SALE);
        status = SpuStatus.ON_SALE;
    }

    /**
     * 下架。
     */
    public void offShelf() {
        status.assertTransitionTo(SpuStatus.OFF_SALE);
        status = SpuStatus.OFF_SALE;
    }

    /**
     * 是否允许删除：仅在售商品不可删（先下架再删），草稿与下架商品可删。
     *
     * @return 允许删除返回 true
     */
    public boolean canDelete() {
        return status != SpuStatus.ON_SALE;
    }

    /**
     * 是否包含指定 SKU。
     *
     * @param skuId SKU ID
     * @return 存在返回 true
     */
    public boolean containsSku(Long skuId) {
        for (Sku sku : skus) {
            if (sku.getId() != null && sku.getId().equals(skuId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 ID 查找 SKU。
     *
     * @param skuId SKU ID
     * @return SKU；不存在返回 null
     */
    public Sku findSku(Long skuId) {
        for (Sku sku : skus) {
            if (sku.getId() != null && sku.getId().equals(skuId)) {
                return sku;
            }
        }
        return null;
    }
}
