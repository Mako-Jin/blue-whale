package com.bluewhale.mall.aftersale.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 申请售后命令。
 *
 * <p>只传「退哪几行、各退几件」，不传金额：金额由服务端按订单行快照价 × 数量计算。
 * 让前端传金额和让前端传价格是同一类错误——金额只能有一个计算来源。</p>
 *
 * @author BlueWhale
 */
@Data
public class AfterSaleApplyCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    @NotNull(message = "订单不能为空")
    private Long orderId;

    /** 售后类型：REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 */
    private String type;

    /** 售后原因 */
    private String reason;

    /** 问题描述 */
    private String description;

    /** 凭证图片 URL（逗号分隔） */
    private String images;

    /** 退回物流单号（退货退款可选） */
    private String returnLogisticsNo;

    /** 售后明细 */
    @Valid
    @NotEmpty(message = "请选择要退的商品")
    private List<ApplyLine> lines = new ArrayList<>();

    /**
     * 售后行。
     */
    @Data
    public static class ApplyLine implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 订单明细 ID */
        @NotNull(message = "订单明细不能为空")
        private Long orderItemId;

        /** 本次退数量 */
        @NotNull(message = "退数量不能为空")
        @Positive(message = "退数量必须大于 0")
        private Integer quantity;
    }
}
