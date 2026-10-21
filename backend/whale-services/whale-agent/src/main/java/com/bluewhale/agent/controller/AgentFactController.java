package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.FactVO;
import com.bluewhale.agent.entity.AgentFact;
import com.bluewhale.agent.service.FactStoreService;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化事实查询接口（{@code GET /agent/facts}）。
 *
 * <p>按 {@code type + key} 精确查询端口、路由、依赖版本、表字段、提交等事实，
 * 受 {@code sys:agent:search} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentFactController {

    private final FactStoreService factStoreService;

    /**
     * 查询结构化事实。
     *
     * @param type  事实类型（可选：SERVICE/ROUTE/PORT/DEPENDENCY/TABLE/COLUMN/COMMIT/ENDPOINT/RUNTIME_SERVICE）
     * @param key   事实键（可选，如服务名、表名、{@code groupId:artifactId}）
     * @param limit 返回条数（可选，默认 50，上限 500）
     * @return 事实列表
     */
    @GetMapping("/facts")
    @RequirePermission("sys:agent:search")
    public Result<List<FactVO>> facts(@RequestParam(value = "type", required = false) String type,
                                      @RequestParam(value = "key", required = false) String key,
                                      @RequestParam(value = "limit", required = false) Integer limit) {
        List<AgentFact> facts = factStoreService.query(type, key, limit);
        List<FactVO> result = new ArrayList<>(facts.size());
        for (AgentFact fact : facts) {
            result.add(FactVO.builder()
                    .id(String.valueOf(fact.getId()))
                    .factType(fact.getFactType())
                    .factKey(fact.getFactKey())
                    .value(factStoreService.parseValue(fact.getValueJson()))
                    .sourcePath(fact.getSourcePath())
                    .capturedAt(FactStoreService.formatTime(fact.getCapturedAt()))
                    .build());
        }
        return Result.ok(result);
    }
}
