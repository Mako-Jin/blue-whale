package com.bluewhale.mall.catalog.application.command;

import com.bluewhale.mall.catalog.domain.model.StockInbound;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * WMS 入库命令。
 *
 * <p>WMS/ERP 经内网接口推送：单号幂等，重复推送返回首次入库结果。
 * 行必须给 SKU ID（编码仅作快照展示，不做寻址依据——编码可能被运营改名，
 * 用它寻址会写错库存）。</p>
 *
 * @author BlueWhale
 */
@Data
public class StockInboundCommand {

    /** 入库单号（租户内唯一，幂等键） */
    private String docNo;

    /** 来源系统：WMS / ERP / MANUAL（空默认 WMS） */
    private String source;

    /** 备注 */
    private String remark;

    /** 明细行 */
    private List<InboundLine> lines = new ArrayList<>();

    /**
     * 转领域对象。
     *
     * @return 入库单领域对象
     */
    public StockInbound toDomain() {
        StockInbound inbound = new StockInbound();
        inbound.setDocNo(docNo);
        inbound.setSource(source);
        inbound.setRemark(remark);
        if (lines != null) {
            for (InboundLine line : lines) {
                StockInbound.StockInboundLine domainLine = new StockInbound.StockInboundLine();
                domainLine.setSkuId(line.getSkuId());
                domainLine.setSkuCode(line.getSkuCode());
                domainLine.setQuantity(line.getQuantity());
                inbound.getLines().add(domainLine);
            }
        }
        return inbound;
    }

    /**
     * 入库明细行命令。
     */
    @Data
    public static class InboundLine {

        /** SKU ID */
        private Long skuId;

        /** SKU 编码（快照） */
        private String skuCode;

        /** 入库数量（正整数） */
        private Integer quantity;
    }
}
