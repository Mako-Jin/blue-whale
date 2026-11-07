package com.bluewhale.mes.dispatch.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.dispatch.dto.MesWorkOrderReceiveRequest;
import com.bluewhale.mes.dispatch.service.MesDispatchService;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderProgressVO;
import com.bluewhale.mes.vo.MesWorkOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 内网工单接收接口
 *
 * <p>供 ERP 下发生产订单：网关 {@code internal-deny} 对 {@code /mes/internal/**} 返回 403，
 * 服务侧由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}。按 ERP 单号幂等。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/internal/work-order")
@RequiredArgsConstructor
public class MesInternalWorkOrderController {

    private final MesDispatchService dispatchService;

    /**
     * 接收 ERP 生产工单
     *
     * @param request 下发请求
     * @return 工单视图
     */
    @PostMapping
    public Result<MesWorkOrderVO> receive(@Valid @RequestBody MesWorkOrderReceiveRequest request) {
        return Result.ok(dispatchService.receive(request));
    }

    /**
     * 按 ERP 单号查询工单进度（供 ERP 对账拉取）
     *
     * @param erpOrderNo ERP 生产订单号
     * @return 工单进度
     */
    @GetMapping("/{erpOrderNo}")
    public Result<MesWorkOrderProgressVO> progress(@PathVariable String erpOrderNo) {
        return Result.ok(dispatchService.progress(erpOrderNo));
    }
}
