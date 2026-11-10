package com.bluewhale.pdm.integration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CAD 装配 EBOM 导入请求
 *
 * <p>外部 CAD 工具按产品编码与子件编码导入结构；{@code externalKey} 为工具侧
 * 唯一键，重复导入同一键直接返回上次创建的 BOM，不重复建单。</p>
 *
 * @author BlueWhale
 */
@Data
public class PdmIntCadEbomImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 外部幂等键（CAD 装配文件唯一标识） */
    @NotBlank(message = "外部幂等键不能为空")
    private String externalKey;

    /** 产品编码 */
    @NotBlank(message = "产品编码不能为空")
    private String partNo;

    /** BOM 编号，留空按产品编码自动生成 */
    private String bomNo;

    /** 视图，默认 EBOM */
    private String view;

    /** 备注 */
    private String remark;

    /** 子件行 */
    @Valid
    @NotEmpty(message = "子件行不能为空")
    private List<Line> lines = new ArrayList<>();

    /**
     * 子件行
     */
    @Data
    public static class Line implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 子件编码 */
        @NotBlank(message = "子件编码不能为空")
        private String childPartNo;

        /** 用量 */
        @NotNull(message = "用量不能为空")
        @DecimalMin(value = "0.0001", message = "用量必须大于 0")
        private BigDecimal qty;

        /** 单位 */
        private String uom;

        /** 损耗率 */
        private BigDecimal lossRate;

        /** 备注 */
        private String remark;
    }
}
