package com.bluewhale.music.application.command;

import lombok.Data;

/**
 * 版权下架申诉提交命令。
 *
 * @author BlueWhale
 */
@Data
public class AppealSubmitCommand {

    /** 申诉理由（纯文本；空与长度统一由领域校验，返回 40022） */
    private String reason;
}
