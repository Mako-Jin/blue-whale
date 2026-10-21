package com.bluewhale.agent.support.facts;

import java.nio.file.Path;
import java.util.List;

/**
 * 结构化事实采集器：从仓库文件或运行时环境抽取 {@link FactRecord}。
 *
 * <p>实现类均为无状态组件，采集失败时返回空列表并记录告警，不阻断其它采集器。</p>
 *
 * @author BlueWhale
 */
public interface FactCollector {

    /**
     * 执行采集。
     *
     * @param repoRoot 仓库根目录
     * @return 事实列表（可能为空）
     */
    List<FactRecord> collect(Path repoRoot);
}
