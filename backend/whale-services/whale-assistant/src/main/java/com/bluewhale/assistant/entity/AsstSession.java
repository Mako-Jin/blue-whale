package com.bluewhale.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 助手会话实体，对应表 {@code asst_session}。
 *
 * <p>按用户隔离：{@code userId} 取自网关 {@code X-User-Id}，查询与删除均强制携带该条件。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asst_session")
public class AsstSession extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属助手ID（asst_definition.id） */
    private Long assistantId;

    /** 所属用户ID（网关注入 X-User-Id） */
    private Long userId;

    /** 会话标题（默认取首条消息前 50 字） */
    private String title;

    /** 来源：platform 工作台 / embed 业务内嵌 / openapi 开放接口 */
    private String source;

    /** 业务侧会话引用（内嵌方回传标识，可空） */
    private String channelRef;
}
