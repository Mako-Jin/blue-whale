package com.bluewhale.archive.controller;

import com.bluewhale.archive.dto.ArchiveIngestRequest;
import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.service.ArchiveIngestService;
import com.bluewhale.archive.vo.ArchiveFileSecurityVO;
import com.bluewhale.archive.vo.ArchiveIngestResultVO;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案服务内网接口。
 *
 * <p>服务内路径前缀 {@code /archive/internal}，网关 {@code internal-deny} 对
 * {@code /archive/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * <p>读接口供 whale-agent 知识库采集查询文件密级；写接口供业务服务（whale-contract）
 * 在单据终态时推送不可变快照入档，按 {@code (sourceType, sourceId)} 幂等。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/archive/internal")
@RequiredArgsConstructor
public class ArchiveInternalController {

    private final ArchiveFileMapper archiveFileMapper;

    private final ArchiveIngestService ingestService;

    /**
     * 查询文件所属档案条目的密级。
     *
     * @param fileId 文件ID
     * @return 密级视图；未归档返回 {@code data=null}
     */
    @GetMapping("/files/{fileId}/security")
    public Result<ArchiveFileSecurityVO> security(@PathVariable Long fileId) {
        return Result.ok(archiveFileMapper.selectSecurityByFileId(fileId));
    }

    /**
     * 业务快照入档：生成「待整理」档案条目，档号在确认归档时分配。
     *
     * @param request 快照归档请求
     * @return 归档结果（幂等命中时 {@code created=false}）
     */
    @PostMapping("/items")
    public Result<ArchiveIngestResultVO> ingest(@Valid @RequestBody ArchiveIngestRequest request) {
        return Result.ok(ingestService.ingest(request));
    }
}
