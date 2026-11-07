package com.bluewhale.erp.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.finance.application.command.PayableCreateCommand;
import com.bluewhale.erp.finance.application.service.ArApAppService;
import com.bluewhale.erp.finance.application.view.PayableView;
import com.bluewhale.erp.scm.application.command.PurchaseOrderCreateCommand;
import com.bluewhale.erp.scm.application.service.PurchaseAppService;
import com.bluewhale.erp.scm.application.view.PurchaseOrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERP 面向 OA 的内网接口。
 *
 * <p>仅限服务间 Nacos 直连，网关 {@code internal-deny} 统一拦截外部访问。
 * 应付单按 {@code sourceType + sourceId} 幂等；采购订单草稿按来源 ID 生成固定单号实现幂等。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/oa")
@RequiredArgsConstructor
public class ErpInternalOaController {

    /** OA 采购订单单号前缀 */
    private static final String OA_PURCHASE_DOC_PREFIX = "OA-PO-";

    private final ArApAppService arApAppService;

    private final PurchaseAppService purchaseAppService;

    /**
     * 创建 OA 报销应付单（幂等）。
     *
     * @param command 应付创建命令
     * @return 应付视图
     */
    @PostMapping("/payable")
    public Result<PayableView> createPayable(@Valid @RequestBody PayableCreateCommand command) {
        return Result.ok(arApAppService.createPayable(command));
    }

    /**
     * 创建 OA 采购订单草稿（幂等）。
     *
     * @param request 采购订单草稿请求
     * @return 采购订单号
     */
    @PostMapping("/purchase-order/draft")
    public Result<Map<String, Object>> createPurchaseOrderDraft(
            @Valid @RequestBody OaPurchaseOrderDraftRequest request) {
        PurchaseOrderCreateCommand command = new PurchaseOrderCreateCommand();
        command.setDocNo(OA_PURCHASE_DOC_PREFIX + request.getSourceId());
        command.setSupplierId(request.getSupplierId());
        command.setWarehouseId(request.getWarehouseId());
        command.setDocDate(request.getDocDate() == null ? LocalDate.now() : request.getDocDate());
        command.setExpectedDate(request.getExpectedDate());
        command.setCurrency(request.getCurrency());
        command.setRemark(request.getRemark());
        command.setLines(request.getLines().stream().map(line -> {
            PurchaseOrderCreateCommand.Line target = new PurchaseOrderCreateCommand.Line();
            target.setItemCode(line.getItemCode());
            target.setQuantity(line.getQuantity());
            target.setUnitPrice(line.getUnitPrice());
            target.setUom(line.getUom());
            return target;
        }).toList());
        PurchaseOrderView view = purchaseAppService.create(command);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("poNo", view.getDocNo());
        data.put("id", view.getId());
        return Result.ok(data);
    }

    /**
     * OA 采购订单草稿请求。
     */
    @Data
    public static class OaPurchaseOrderDraftRequest {

        /** 来源类型 */
        private String sourceType;

        /** 来源采购申请ID */
        @NotNull(message = "来源单据ID不能为空")
        private Long sourceId;

        /** 来源单号 */
        private String sourceCode;

        /** 供应商ID（ERP 主数据，必填） */
        @NotNull(message = "供应商ID不能为空，请先在 ERP 维护供应商主数据")
        private Long supplierId;

        /** 收货仓库ID（ERP 主数据，必填） */
        @NotNull(message = "收货仓库ID不能为空，请先在 ERP 维护仓库主数据")
        private Long warehouseId;

        /** 单据日期 */
        private LocalDate docDate;

        /** 期望到货日期 */
        private LocalDate expectedDate;

        /** 币种 */
        private String currency;

        /** 备注 */
        private String remark;

        /** 明细 */
        @Valid
        @NotEmpty(message = "采购明细不能为空")
        private List<Line> lines;

        /**
         * 采购明细行。
         */
        @Data
        public static class Line {

            /** 物料编码 */
            @NotBlank(message = "物料编码不能为空")
            private String itemCode;

            /** 物料名称 */
            private String itemName;

            /** 数量 */
            @NotNull(message = "数量不能为空")
            private BigDecimal quantity;

            /** 单价 */
            private BigDecimal unitPrice;

            /** 单位 */
            private String uom;
        }
    }
}
