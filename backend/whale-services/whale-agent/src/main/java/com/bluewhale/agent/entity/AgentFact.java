package com.bluewhale.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 结构化事实实体，对应表 {@code agent_fact}（Wave 5.47 双通道回答的「事实通道」）。
 *
 * <p>端口、网关路由、依赖版本、表结构、服务清单等有唯一答案的知识落本表，
 * 由采集器写入，问答链路按 {@code factType + factKey} 精确命中，避免向量检索幻觉。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_fact")
public class AgentFact extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 事实类型：SERVICE / ROUTE / PORT / DEPENDENCY / TABLE / COLUMN / COMMIT / ENDPOINT / RUNTIME_SERVICE */
    private String factType;

    /** 事实键（如服务名、路由 id、表名、groupId:artifactId） */
    private String factKey;

    /** 事实值 JSON（结构化，保留原始层级） */
    private String valueJson;

    /** 来源路径（仓库相对路径或 git 引用），用于回答时标注出处 */
    private String sourcePath;

    /** 采集时间（覆盖式更新，独立于 create_time/update_time） */
    private LocalDateTime capturedAt;
}
