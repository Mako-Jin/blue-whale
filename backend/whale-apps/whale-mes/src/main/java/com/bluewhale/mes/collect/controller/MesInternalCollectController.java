package com.bluewhale.mes.collect.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.collect.dto.MesMetricIngestRequest;
import com.bluewhale.mes.collect.service.MesCollectService;
import com.bluewhale.mes.collect.vo.MesMetricIngestResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 内网样本接收接口
 *
 * <p>供边缘网关推送采集样本：网关 {@code internal-deny} 对 {@code /mes/internal/**} 返回 403，
 * 服务侧由 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}。
 * 按 {@code (point, sampledAt, seq)} 幂等，网关重推不会重复入库。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/internal/collect")
@RequiredArgsConstructor
public class MesInternalCollectController {

    private final MesCollectService collectService;

    /**
     * 接收网关样本
     *
     * @param pointCode 采集点编码
     * @param request   样本批次
     * @return 上报结果
     */
    @PostMapping("/{pointCode}")
    public Result<MesMetricIngestResultVO> ingest(@PathVariable String pointCode,
                                                  @Valid @RequestBody MesMetricIngestRequest request) {
        return Result.ok(collectService.ingest(pointCode, request));
    }
}
