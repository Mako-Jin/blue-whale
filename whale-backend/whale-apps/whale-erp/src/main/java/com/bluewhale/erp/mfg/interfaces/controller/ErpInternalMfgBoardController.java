package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.mfg.application.service.MfgBoardAppService;
import com.bluewhale.erp.mfg.application.view.MfgBoardView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 制造看板内网契约接口。
 *
 * <p>供 whale-scene 3D 看板取实时数量。网关已将 {@code /erp/internal/**}
 * 加入 internal-deny，外部访问 403；服务仅做只读聚合。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/internal/mfg")
@RequiredArgsConstructor
public class ErpInternalMfgBoardController {

    private final MfgBoardAppService mfgBoardAppService;

    /**
     * 生产订单与库存余额的实时数量。
     *
     * @param limit 单类记录上限（默认 50，最大 200）
     * @return 看板视图
     */
    @GetMapping("/board")
    public Result<MfgBoardView> board(@RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(mfgBoardAppService.board(limit));
    }
}
