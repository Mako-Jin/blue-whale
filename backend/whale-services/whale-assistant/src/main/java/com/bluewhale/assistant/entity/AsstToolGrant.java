package com.bluewhale.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 助手工具授权实体，对应表 {@code asst_tool_grant}。
 *
 * <p>工具编码对齐 {@code whale-ai} 的 {@code /v1/tools} 注册表；白名单只能收敛、不能放大。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asst_tool_grant")
public class AsstToolGrant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属助手ID（asst_definition.id） */
    private Long assistantId;

    /** 工具编码（如 knowledge_search / fact_get） */
    private String toolCode;

    /** 授权状态：0 未授权 / 1 已授权 */
    private Integer granted;

    /** 授权审批人用户ID */
    private Long approvedBy;
}
