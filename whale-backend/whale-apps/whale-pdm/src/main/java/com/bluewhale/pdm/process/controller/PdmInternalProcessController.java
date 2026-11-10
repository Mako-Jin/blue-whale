package com.bluewhale.pdm.process.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.process.service.PdmPrcRouteService;
import com.bluewhale.pdm.process.vo.PdmPrcRouteSnapshotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工艺路线内网契约控制器（供 ERP CRP 读取已发布快照）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/internal/process/routes")
@RequiredArgsConstructor
public class PdmInternalProcessController {

    private final PdmPrcRouteService routeService;

    /**
     * 读取零件最新已发布路线快照。
     *
     * @param partCode 零件编码
     * @return 路线快照
     */
    @GetMapping("/by-part/{partCode}")
    public Result<PdmPrcRouteSnapshotVO> latestReleased(@PathVariable String partCode) {
        return Result.ok(routeService.latestReleased(partCode));
    }

    /**
     * 按编号 + 版本读取已发布路线快照。
     *
     * @param routeNo   路线编号
     * @param versionNo 版本号
     * @return 路线快照
     */
    @GetMapping("/{routeNo}/versions/{versionNo}")
    public Result<PdmPrcRouteSnapshotVO> snapshot(@PathVariable String routeNo,
                                                  @PathVariable String versionNo) {
        return Result.ok(routeService.snapshot(routeNo, versionNo));
    }
}
