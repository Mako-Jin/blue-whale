package com.bluewhale.mall.aftersale.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 换货补发发货命令。
 *
 * <p>只传承运商与运单号，不传明细：补发明细固定为售后单的换货行，
 * 由服务端从售后单构造——让前端传明细就给了「补发别的商品」的口子。</p>
 *
 * @author BlueWhale
 */
@Data
public class AfterSaleExchangeShipCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 承运商编码（{@code CarrierType}） */
    @NotBlank(message = "请选择承运商")
    private String carrierCode;

    /** 承运商名称：{@code OTHER} 时用于显示自定名称，其余可空（取枚举默认名） */
    @Size(max = 64, message = "承运商名称不得超过 64 字符")
    private String carrierName;

    /** 运单号（必填，自送也填内部单号） */
    @NotBlank(message = "请填写运单号")
    @Size(max = 64, message = "运单号不得超过 64 字符")
    private String trackingNo;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
