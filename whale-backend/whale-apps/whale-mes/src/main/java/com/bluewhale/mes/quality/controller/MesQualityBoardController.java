package com.bluewhale.mes.quality.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.quality.service.MesInspectionService;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 质量看板接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/quality/board", version = "1")
@RequiredArgsConstructor
public class MesQualityBoardController {

    private final MesInspectionService inspectionService;

    /**
     * 质量看板聚合
     *
     * @param boardDate 统计日期（yyyy-MM-dd，空则当天）
     * @param lineId    产线 ID
     * @return 看板视图
     */
    @GetMapping
    @RequirePermission("sys:mes:quality:list")
    public Result<MesQualityBoardVO> board(@RequestParam(required = false) String boardDate,
                                           @RequestParam(required = false) Long lineId) {
        return Result.ok(inspectionService.board(boardDate, lineId));
    }
}
