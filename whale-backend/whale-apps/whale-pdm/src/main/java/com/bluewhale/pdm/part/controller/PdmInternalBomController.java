package com.bluewhale.pdm.part.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.vo.PdmBomSnapshotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDM BOM 内网契约接口
 *
 * <p>供 ERP 按 {@code bomNo + versionNo} 幂等拉取已发布快照，并记录拉取日志用于对账。
 * 网关已将 {@code /pdm/internal/**} 加入 internal-deny，外部访问 403；
 * 服务内由内网 token 过滤器校验，因此不叠加功能权限。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/internal/bom")
@RequiredArgsConstructor
public class PdmInternalBomController {

    private final PdmBomService bomService;

    private final PdmIntSyncService syncService;

    /**
     * 读取已发布 BOM 快照
     *
     * @param bomNo     BOM 编号
     * @param versionNo 版本号
     * @return 稳定快照
     */
    @GetMapping("/{bomNo}/versions/{versionNo}")
    public Result<PdmBomSnapshotVO> snapshot(@PathVariable String bomNo,
                                             @PathVariable String versionNo) {
        PdmBomSnapshotVO snapshot = bomService.snapshot(bomNo, versionNo);
        syncService.recordBomPull(bomNo, versionNo, snapshot.getChecksum());
        return Result.ok(snapshot);
    }
}
