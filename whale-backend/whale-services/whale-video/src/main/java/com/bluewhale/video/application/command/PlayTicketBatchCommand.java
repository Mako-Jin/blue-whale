package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量播放票据申请命令（feed / 列表预取）。
 *
 * @author BlueWhale
 */
@Data
public class PlayTicketBatchCommand {

    /** 内容 ID 列表（一次最多 20 个） */
    @NotEmpty(message = "{validation.video.play-ticket.content-ids-required}")
    private List<Long> contentIds;
}
