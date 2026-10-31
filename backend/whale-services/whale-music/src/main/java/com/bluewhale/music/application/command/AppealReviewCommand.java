package com.bluewhale.music.application.command;

import lombok.Data;

/**
 * 版权下架申诉复核命令。
 *
 * @author BlueWhale
 */
@Data
public class AppealReviewCommand {

    /** 审核意见，可为空；超过 500 个字符返回 40022 */
    private String opinion;
}
