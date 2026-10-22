package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.SearchHitVO;
import com.bluewhale.agent.service.AgentSearchService;
import com.bluewhale.agent.support.index.SearchHit;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识检索调试接口（{@code GET /agent/search}）。
 *
 * <p>只做纯检索、不生成回答，用于排查索引质量与问答链路证据来源；
 * 受 {@code sys:agent:search} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentSearchController {

    private final AgentSearchService searchService;

    /**
     * 检索知识块。
     *
     * @param q 查询文本
     * @param topK 返回条数（可选，默认 8，上限 50）
     * @param kind 知识类型过滤（可选，逗号分隔：code,doc,sql,config）
     * @return 命中结果列表
     */
    @GetMapping("/search")
    @RequirePermission("sys:agent:search")
    @AuditLog(operation = "知识检索", type = OperateType.QUERY, targetType = "agent_index")
    public Result<List<SearchHitVO>> search(@RequestParam("q") String q,
                                            @RequestParam(value = "topK", required = false) Integer topK,
                                            @RequestParam(value = "kind", required = false) String kind) {
        List<SearchHit> hits = searchService.search(q, topK, AgentSearchService.parseKinds(kind));
        List<SearchHitVO> result = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            result.add(SearchHitVO.builder()
                    .id(hit.getId())
                    .kind(hit.getKind())
                    .module(hit.getModule())
                    .path(hit.getPath())
                    .title(hit.getTitle())
                    .content(hit.getContent())
                    .startLine(hit.getStartLine())
                    .endLine(hit.getEndLine())
                    .wave(hit.getWave())
                    .score(hit.getScore())
                    .build());
        }
        return Result.ok(result);
    }
}
