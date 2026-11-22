package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mall.catalog.domain.model.PriceType;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.common.enums.CurrencyType;
import com.bluewhale.mall.common.enums.MallMode;
import com.bluewhale.mall.common.enums.SettlementType;
import com.bluewhale.mall.interfaces.view.MallMetaView;
import com.bluewhale.mall.order.domain.model.OrderStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 商城门面接口。
 *
 * <p>把枚举一次性交给前端：形态、结算方式、币种、价目、订单状态。
 * 前端硬编码一份枚举的后果是「后端加了第四种形态，前端下拉框里没有」，
 * 而且这种缺口不会报错，只会让运营以为新形态没生效。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/meta", version = "1")
public class MallMetaController {

    /** 版本标识 */
    private static final String VERSION = "M0";

    /**
     * 门面信息。
     *
     * @return 门面信息
     */
    @GetMapping
    @RequirePermission(MallPermissions.BROWSE)
    public Result<MallMetaView> meta() {
        MallMetaView view = new MallMetaView();
        view.setService("whale-mall");
        view.setVersion(VERSION);
        view.setModes(Arrays.stream(MallMode.values()).map(Enum::name).toList());
        view.setSettlementTypes(Arrays.stream(SettlementType.values()).map(Enum::name).toList());
        view.setCurrencyTypes(Arrays.stream(CurrencyType.values()).map(Enum::name).toList());
        view.setPriceTypes(Arrays.stream(PriceType.values()).map(Enum::name).toList());
        view.setOrderStatuses(Arrays.stream(OrderStatus.values()).map(Enum::name).toList());
        return Result.ok(view);
    }
}
