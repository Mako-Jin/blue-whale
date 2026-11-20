package com.bluewhale.video.interfaces.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 播放票据申请请求体。
 *
 * @author BlueWhale
 */
@Data
public class PlayTicketRequest {

    /** 内容 ID */
    @NotNull(message = "{validation.video.play.content-required}")
    private Long contentId;
}
