package com.bluewhale.agent.controller;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.IndexStatusVO;
import com.bluewhale.agent.entity.AgentIndexMeta;
import com.bluewhale.agent.service.AgentIndexMetaService;
import com.bluewhale.agent.service.KnowledgeIngestService;
import com.bluewhale.agent.support.index.IndexStats;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 索引运维接口：状态查询与全量重建。
 *
 * <p>状态查询登录即可访问（前端顶部徽标）；重建需 {@code sys:agent:index:rebuild} 权限，
 * 触发后异步执行，立即返回是否受理。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent/index")
@RequiredArgsConstructor
public class AgentIndexController {

    private final KnowledgeIngestService ingestService;

    private final AgentIndexMetaService metaService;

    private final AgentProperties properties;

    /**
     * 查询索引状态。
     *
     * @return 索引状态
     */
    @GetMapping("/status")
    public Result<IndexStatusVO> status() {
        IndexStats stats = ingestService.stats();
        AgentIndexMeta meta = metaService.get();
        return Result.ok(IndexStatusVO.builder()
                .status(stats.getStatus())
                .indexVersion(meta == null ? null : meta.getIndexVersion())
                .fileCount(stats.getFileCount())
                .chunkCount(stats.getChunkCount())
                .lastBuildAt(stats.getLastBuildAt() != null ? stats.getLastBuildAt()
                        : (meta == null ? null : meta.getLastBuildAt()))
                .buildMillis(stats.getBuildMillis())
                .indexDir(properties.resolveIndexDir().toString())
                .build());
    }

    /**
     * 触发全量重建（异步）。
     *
     * @return {@code true} 已受理 / {@code false} 已有构建任务在执行
     */
    @PostMapping("/rebuild")
    @RequirePermission("sys:agent:index:rebuild")
    public Result<Boolean> rebuild() {
        return Result.ok(ingestService.rebuildAsync());
    }
}
