package com.bluewhale.mall.catalog.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品保存命令（SPU 聚合：商品 + 规格 + 价目）。
 *
 * <p>保存是<b>全量覆盖</b>：提交的 SKU 列表即最终列表，未提交的旧 SKU 会被删除。
 * 因此编辑页必须先加载完整详情再提交，不能只传改动的那一个 SKU。</p>
 *
 * @author BlueWhale
 */
@Data
public class SpuSaveCommand {

    /** 商品编码（租户内唯一） */
    @NotBlank(message = "{validation.mall.spu.spu-code-required}")
    @Size(max = 64, message = "{validation.mall.spu.spu-code-size}")
    private String spuCode;

    /** 商品名称 */
    @NotBlank(message = "{validation.mall.spu.spu-name-required}")
    @Size(max = 128, message = "{validation.mall.spu.spu-name-size}")
    private String spuName;

    /** 所属类目 ID */
    @NotNull(message = "{validation.mall.spu.category-id-required}")
    private Long categoryId;

    /** 品牌 */
    @Size(max = 64, message = "{validation.mall.spu.brand-size}")
    private String brand;

    /** 主图（文件 ID 或 URL） */
    @Size(max = 512, message = "{validation.mall.spu.main-image-size}")
    private String mainImage;

    /** 轮播图（文件 ID 或 URL 列表） */
    private List<String> images = new ArrayList<>();

    /** 图文详情 */
    private String detail;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "{validation.mall.spu.remark-size}")
    private String remark;

    /** 规格列表（至少一项） */
    @Valid
    @NotEmpty(message = "{validation.mall.spu.skus-required}")
    private List<SkuCommand> skus = new ArrayList<>();
}
