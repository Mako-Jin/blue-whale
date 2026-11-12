package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 回帖请求。
 *
 * <p>M1 只支持一级楼层（{@code floor} 由服务端分配）。{@code replyToPostId} 用于「引用回复」，
 * 引用的楼层即使被删也保留引用关系，展示时占位为「该楼层已删除」。</p>
 *
 * @author BlueWhale
 */
@Data
public class PostCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 正文（富文本源码，服务端消毒） */
    @NotBlank(message = "回复内容不能为空")
    private String content;

    /** 引用楼层ID（可空） */
    private Long replyToPostId;
}
