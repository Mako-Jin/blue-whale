package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量播放票据视图（feed / 列表预取）。
 *
 * @author BlueWhale
 */
@Data
public class PlayTicketBatchView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 票据列表（不可播放的内容不会出现在列表中） */
    private List<PlayTicketView> tickets;

    /** 申请但当前不可播放的内容 ID */
    private List<Long> rejectedContentIds;
}
