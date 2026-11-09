package com.bluewhale.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 助手定义实体，对应表 {@code asst_definition}。
 *
 * <p>多助手运营核心：系统提示词与模型参数随请求下发 {@code whale-ai}（本服务不持模型凭据）；
 * 工具白名单存 {@code asst_tool_grant}，不落在本表。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asst_definition")
public class AsstDefinition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 助手编码（租户内唯一，如 platform-helper） */
    private String code;

    /** 助手名称 */
    private String name;

    /** 头像（whale-file 附件URL） */
    private String avatar;

    /** 助手简介 */
    private String description;

    /** 系统提示词（装配进 whale-ai Graph，上限 8000 字符） */
    private String systemPrompt;

    /** 模型参数 JSON（仅 model/temperature/maxTokens） */
    private String modelParamsJson;

    /** 知识范围（透传 whale-agent 检索 scope，空=全部） */
    private String knowledgeScope;

    /** 可见范围 JSON（租户/部门/用户白名单；空=租户内全部可见） */
    private String visibilityJson;

    /** 状态：draft 草稿 / published 已发布 / disabled 已停用 */
    private String status;

    /** 创建人用户ID */
    private Long createdBy;
}
