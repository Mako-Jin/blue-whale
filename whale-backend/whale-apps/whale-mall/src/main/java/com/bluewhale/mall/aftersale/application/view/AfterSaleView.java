package com.bluewhale.mall.aftersale.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 售后单视图。
 *
 * @author BlueWhale
 */
@Data
public class AfterSaleView {

    /** 售后单 ID */
    private Long id;

    /** 售后单号 */
    private String afterSaleNo;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 买家用户 ID */
    private Long userId;

    /** 类型：REFUND_ONLY / RETURN_REFUND / EXCHANGE */
    private String type;

    /** 状态：CREATED / WAIT_RETURN / APPROVED / REFUNDED / EXCHANGING / EXCHANGED / REJECTED / CANCELLED */
    private String status;

    /** 本次申请退款金额 */
    private BigDecimal refundAmount;

    /** 售后原因 */
    private String reason;

    /** 问题描述 */
    private String description;

    /** 凭证图片（逗号分隔原始串） */
    private String images;

    /** 凭证图片列表（images 拆分，前端直接渲染） */
    private List<String> imagesList = new ArrayList<>();

    /** 审核人 ID */
    private Long auditUserId;

    /** 审核人名称 */
    private String auditUserName;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核意见 */
    private String auditRemark;

    /** 支付侧退款流水号 */
    private String refundNo;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 退回物流单号 */
    private String returnLogisticsNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 售后明细 */
    private List<AfterSaleLineView> items = new ArrayList<>();
}
