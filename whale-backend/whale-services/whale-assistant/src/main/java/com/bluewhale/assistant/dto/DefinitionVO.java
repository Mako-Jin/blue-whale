package com.bluewhale.assistant.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 助手定义视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class DefinitionVO {

    /** 助手ID（字符串） */
    private String id;

    /** 助手编码 */
    private String code;

    /** 助手名称 */
    private String name;

    /** 头像地址 */
    private String avatar;

    /** 简介 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /** 模型参数 */
    private Map<String, Object> modelParams;

    /** 知识范围 */
    private String knowledgeScope;

    /** 状态：draft / published / disabled */
    private String status;

    /** 已授权工具编码（granted=1） */
    private List<String> toolWhitelist;

    /** 创建人用户ID（字符串） */
    private String createdBy;

    /** 创建时间（ISO-8601） */
    private String createTime;

    /** 更新时间（ISO-8601） */
    private String updateTime;
}
