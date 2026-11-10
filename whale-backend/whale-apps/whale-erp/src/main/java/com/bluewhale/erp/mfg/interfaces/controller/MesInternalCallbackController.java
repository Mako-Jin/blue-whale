package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.mfg.application.command.MesCallbackRequest;
import com.bluewhale.erp.mfg.application.service.MesIntegrationAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 内网回传接口（完工/进度）。
 *
 * <p>网关 {@code internal-deny} 对 {@code /erp/internal/**} 返回 403，服务侧由
 * {@code InternalTokenFilter} 校验内网令牌；不配置业务权限码。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/production-order")
@RequiredArgsConstructor
public class MesInternalCallbackController {

    private final MesIntegrationAppService mesIntegrationAppService;

    /**
     * MES 完工回传。
     *
     * @param erpOrderNo ERP 生产订单号
     * @param request    事件键与报文
     * @return 处理结果
     */
    @PostMapping("/{erpOrderNo}/completion")
    public Result<Void> completion(@PathVariable String erpOrderNo,
                                   @Valid @RequestBody MesCallbackRequest request) {
        mesIntegrationAppService.handleCompletion(request.getEventId(), request.getPayload());
        return Result.ok();
    }

    /**
     * MES 进度回传。
     *
     * @param erpOrderNo ERP 生产订单号
     * @param request    事件键与报文
     * @return 处理结果
     */
    @PostMapping("/{erpOrderNo}/progress")
    public Result<Void> progress(@PathVariable String erpOrderNo,
                                 @Valid @RequestBody MesCallbackRequest request) {
        mesIntegrationAppService.handleProgress(request.getEventId(), request.getPayload());
        return Result.ok();
    }
}
