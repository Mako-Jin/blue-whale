package com.bluewhale.mall.aftersale.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 回填退货物流单号命令。
 *
 * <p>买家寄回商品后补录运单号：售后单上不落这个号，运营确认收货时就只能
 * 凭记忆对包裹，扯皮没有凭据。</p>
 *
 * @author BlueWhale
 */
@Data
public class AfterSaleReturnLogisticsCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退货物流单号 */
    @NotBlank(message = "退货物流单号不能为空")
    @Size(max = 64, message = "退货物流单号不得超过 64 字符")
    private String returnLogisticsNo;
}
