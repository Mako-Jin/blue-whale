package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.Transfer;
import com.bluewhale.erp.scm.domain.model.TransferLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存调拨单视图。
 *
 * @author BlueWhale
 */
@Data
public class TransferView {

    /** 主键 */
    private Long id;

    /** 调拨单号 */
    private String docNo;

    /** 调拨日期 */
    private LocalDate transferDate;

    /** 调出仓库 */
    private Long fromWarehouseId;

    /** 调出仓库名称 */
    private String fromWarehouseName;

    /** 调入仓库 */
    private Long toWarehouseId;

    /** 调入仓库名称 */
    private String toWarehouseName;

    /** 状态 */
    private String status;

    /** 调拨总数量 */
    private BigDecimal totalQuantity;

    /** 调拨总金额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 分录 */
    private List<LineView> lines;

    /**
     * 由领域对象构建视图。
     *
     * @param transfer 调拨单
     * @return 视图
     */
    public static TransferView of(Transfer transfer) {
        TransferView view = new TransferView();
        view.setId(transfer.getId());
        view.setDocNo(transfer.getDocNo());
        view.setTransferDate(transfer.getTransferDate());
        view.setFromWarehouseId(transfer.getFromWarehouseId());
        view.setFromWarehouseName(transfer.getFromWarehouseName());
        view.setToWarehouseId(transfer.getToWarehouseId());
        view.setToWarehouseName(transfer.getToWarehouseName());
        view.setStatus(transfer.getStatus());
        view.setTotalQuantity(transfer.getTotalQuantity());
        view.setTotalAmount(transfer.getTotalAmount());
        view.setRemark(transfer.getRemark());
        if (transfer.getLines() != null) {
            view.setLines(transfer.getLines().stream().map(LineView::of).toList());
        }
        return view;
    }

    /**
     * 调拨分录视图。
     */
    @Data
    public static class LineView {

        /** 行号 */
        private Integer lineNo;

        /** 物料主键 */
        private Long itemId;

        /** 物料编码 */
        private String itemCode;

        /** 物料名称 */
        private String itemName;

        /** 计量单位 */
        private String uom;

        /** 调拨数量 */
        private BigDecimal quantity;

        /** 单位成本 */
        private BigDecimal unitCost;

        /** 调拨金额 */
        private BigDecimal amount;

        /**
         * 由领域对象构建视图。
         *
         * @param line 分录
         * @return 视图
         */
        public static LineView of(TransferLine line) {
            LineView view = new LineView();
            view.setLineNo(line.getLineNo());
            view.setItemId(line.getItemId());
            view.setItemCode(line.getItemCode());
            view.setItemName(line.getItemName());
            view.setUom(line.getUom());
            view.setQuantity(line.getQuantity());
            view.setUnitCost(line.getUnitCost());
            view.setAmount(line.getAmount());
            return view;
        }
    }
}
