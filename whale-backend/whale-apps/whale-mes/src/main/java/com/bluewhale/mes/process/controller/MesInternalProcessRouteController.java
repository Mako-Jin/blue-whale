package com.bluewhale.mes.process.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.process.dto.MesProcessRouteReceiveRequest;
import com.bluewhale.mes.process.dto.MesProcessRouteWithdrawRequest;
import com.bluewhale.mes.process.service.MesProcessRouteService;
import com.bluewhale.mes.process.vo.MesProcessRouteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 工艺路线内网契约接口
 *
 * <p>供 whale-pdm 下发/撤回工艺路线。网关已将 {@code /mes/internal/**} 加入 internal-deny，
 * 外部访问 403；服务内由内网 token 过滤器校验，因此不叠加功能权限。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/internal/process-route")
@RequiredArgsConstructor
public class MesInternalProcessRouteController {

    private final MesProcessRouteService processRouteService;

    /**
     * 接收工艺路线下发
     *
     * @param request 下发内容
     * @return 接收结果
     */
    @PostMapping
    public Result<MesProcessRouteVO> receive(@Valid @RequestBody MesProcessRouteReceiveRequest request) {
        return Result.ok(processRouteService.receive(request));
    }

    /**
     * 撤回工艺路线
     *
     * @param request 撤回请求
     * @return 接收结果
     */
    @PostMapping("/withdraw")
    public Result<MesProcessRouteVO> withdraw(@Valid @RequestBody MesProcessRouteWithdrawRequest request) {
        return Result.ok(processRouteService.withdraw(request));
    }
}
