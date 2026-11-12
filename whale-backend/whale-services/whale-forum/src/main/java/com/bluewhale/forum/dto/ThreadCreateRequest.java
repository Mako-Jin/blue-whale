package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 发帖请求。
 *
 * <p>标题/正文长度上限不放注解里——上限来自可配置的 {@code whale.forum.*}，
 * 写死注解后改配置就与实际校验不一致（提示 120，实际卡 200）。长度由 service 按配置校验。</p>
 *
 * @author BlueWhale
 */
@Data
public class ThreadCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版块ID */
    @NotNull(message = "版块不能为空")
    private Long boardId;

    /** 帖子类型：NORMAL/QA/VOTE/NOTICE/ACTIVITY/ARTICLE（M1 仅 NORMAL/NOTICE） */
    @Pattern(regexp = "^(NORMAL|QA|VOTE|NOTICE|ACTIVITY|ARTICLE)$", message = "帖子类型非法")
    private String threadType = "NORMAL";

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 正文（富文本源码，服务端消毒） */
    @NotBlank(message = "正文不能为空")
    private String content;

    /** 标签，逗号分隔（仅选既有标签） */
    private String tags;
}
