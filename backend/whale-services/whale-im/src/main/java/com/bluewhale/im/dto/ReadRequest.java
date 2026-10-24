package com.bluewhale.im.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 已读上报请求（{@code POST /im/conversations/{id}/read}）。
 *
 * <p>{@code seq} 为客户端已读到的最大消息序号；服务端按会话当前 {@code lastSeq} 收敛，
 * 只允许游标前进，不允许回退。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReadRequest {

    /** 已读到的最大消息序号 */
    @NotNull
    @Min(0)
    private Long seq;
}
