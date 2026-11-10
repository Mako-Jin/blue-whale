package com.bluewhale.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建会话请求（{@code POST /im/conversations}）。
 *
 * <p>{@code SINGLE} 单聊必须且只能指定 1 位对方；{@code GROUP} 群聊必须提供标题，
 * 创建人自动成为群主，成员总数（含创建人）上限 200。{@code BOT} 会话由服务端初始化，不接受客户端创建。</p>
 *
 * @author BlueWhale
 */
@Data
public class CreateConversationRequest {

    /** 会话类型：SINGLE / GROUP */
    @NotBlank
    @Pattern(regexp = "SINGLE|GROUP")
    private String type;

    /** 会话标题（群聊必填，最长 128 字） */
    @Size(max = 128)
    private String title;

    /** 对方/群成员用户ID列表（不含创建人，最多 199 位） */
    @NotEmpty
    @Size(max = 199)
    private List<@NotNull Long> memberIds;
}
