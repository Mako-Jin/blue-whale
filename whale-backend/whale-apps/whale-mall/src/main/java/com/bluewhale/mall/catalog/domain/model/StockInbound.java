package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存入库单（WMS 联动）。
 *
 * <p>WMS/ERP 完成实物入库后经内网接口推送本单：每个 SKU 行把数量
 * 加回商城可售库存。<b>幂等键是 {@code docNo}</b>——WMS 重试推送同一张
 * 入库单时按单号去重直接返回成功，绝不二次加库存；这是库存类接口
 * 唯一能安全重试的方式。</p>
 *
 * @author BlueWhale
 */
@Data
public class StockInbound {

    /** 入库单号最大长度 */
    public static final int DOC_NO_MAX_LENGTH = 64;

    /** 主键 */
    private Long id;

    /** 入库单号（租户内唯一，幂等键） */
    private String docNo;

    /** 来源系统：WMS / ERP / MANUAL */
    private String source;

    /** 明细行 */
    private List<StockInboundLine> lines = new ArrayList<>();

    /** 备注 */
    private String remark;

    /** 入库时间 */
    private LocalDateTime createTime;

    /**
     * 校验并规整入库单。
     */
    public void validate() {
        if (docNo == null || docNo.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID, "入库单号不能为空");
        }
        docNo = docNo.trim();
        if (docNo.length() > DOC_NO_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID,
                    "入库单号不得超过 " + DOC_NO_MAX_LENGTH + " 字符");
        }
        if (source == null || source.isBlank()) {
            source = "WMS";
        }
        source = source.trim().toUpperCase(java.util.Locale.ROOT);
        if (lines == null || lines.isEmpty()) {
            throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID, "入库单必须有至少一行明细");
        }
        for (StockInboundLine line : lines) {
            line.validate();
        }
    }

    /**
     * 入库单明细行。
     */
    @Data
    public static class StockInboundLine {

        /** 主键 */
        private Long id;

        /** 所属入库单 ID */
        private Long inboundId;

        /** SKU ID */
        private Long skuId;

        /** SKU 编码（快照） */
        private String skuCode;

        /** 入库数量（正整数） */
        private Integer quantity;

        /**
         * 校验行字段。
         */
        public void validate() {
            if ((skuId == null || skuId <= 0) && (skuCode == null || skuCode.isBlank())) {
                throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID, "入库明细必须提供 SKU ID 或 SKU 编码");
            }
            if (quantity == null || quantity <= 0) {
                throw MallErrorCode.exception(MallErrorCode.INBOUND_INVALID, "入库数量必须是正整数");
            }
            if (skuCode != null) {
                skuCode = skuCode.trim().toUpperCase(java.util.Locale.ROOT);
            }
        }

        /**
         * 数量规整为整数。
         *
         * @return 数量
         */
        public BigDecimal quantityValue() {
            return BigDecimal.valueOf(quantity);
        }
    }
}
