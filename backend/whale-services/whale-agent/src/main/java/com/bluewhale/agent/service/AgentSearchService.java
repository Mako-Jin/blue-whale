package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.index.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索服务：参数归一化后委托 {@link AgentIndexService} 执行 BM25 检索。
 *
 * <p>后续问答编排的混合检索（向量 + RRF）也在此层扩展，保持控制器与索引实现解耦。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AgentSearchService {

    private final AgentIndexService indexService;

    private final AgentProperties properties;

    /**
     * 执行检索。
     *
     * @param question 用户问题
     * @param topK 期望条数（为空取默认值，超过上限按上限截断）
     * @param kinds 知识类型过滤（空表示不过滤）
     * @return 命中结果
     */
    public List<SearchHit> search(String question, Integer topK, Set<String> kinds) {
        int max = properties.getSearch().getMaxTopK();
        int effective = topK == null ? properties.getSearch().getDefaultTopK() : Math.min(topK, max);
        if (effective <= 0) {
            effective = properties.getSearch().getDefaultTopK();
        }
        return indexService.search(question, effective, kinds);
    }

    /**
     * 解析逗号分隔的知识类型。
     *
     * @param csv 逗号分隔串（如 {@code code,doc}）
     * @return 类型集合；入参为空返回空集合
     */
    public static Set<String> parseKinds(String csv) {
        Set<String> kinds = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return kinds;
        }
        for (String part : csv.split(",")) {
            String kind = part.trim();
            if (!kind.isEmpty()) {
                kinds.add(kind);
            }
        }
        return kinds;
    }
}
