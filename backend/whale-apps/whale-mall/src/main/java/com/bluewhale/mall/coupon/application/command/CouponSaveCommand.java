package com.bluewhale.mall.coupon.application.command;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板保存命令。
 *
 * <p>时间用固定格式接收而不是默认 ISO：antd 的 DatePicker 给出的是带时区的
 * ISO 字符串，直接反序列化到 {@link LocalDateTime} 会失败。固定格式让
 * 前后端的约定显式写在注解上，而不是靠「碰巧两边的默认值一致」。</p>
 *
 * @author BlueWhale
 */
@Data
public class CouponSaveCommand {

    /** 券编码（租户内唯一） */
    @NotBlank(message = "券编码不能为空")
    @Size(max = 64, message = "券编码不得超过 64 字符")
    private String couponCode;

    /** 券名称 */
    @NotBlank(message = "券名称不能为空")
    @Size(max = 128, message = "券名称不得超过 128 字符")
    private String couponName;

    /** 券类型：FULL_REDUCTION / DISCOUNT */
    @NotBlank(message = "必须指定券类型")
    private String couponType;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 满减面额（FULL_REDUCTION 必填） */
    private BigDecimal faceAmount;

    /** 折扣率（DISCOUNT 必填，如 0.85） */
    private BigDecimal discountRate;

    /** 折扣封顶（DISCOUNT 必填） */
    private BigDecimal maxDiscountAmount;

    /** 适用渠道 ID（空 = 全场通用） */
    private Long channelId;

    /** 发行量（0 = 不限） */
    private Integer totalQty;

    /** 每人限领张数 */
    private Integer perUserLimit;

    /** 生效时间 */
    @NotNull(message = "必须指定生效时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    /** 失效时间 */
    @NotNull(message = "必须指定失效时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validTo;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
