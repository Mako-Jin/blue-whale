package com.bluewhale.sts.track.clientevent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 上报结果。
 *
 * <p><b>为什么要把 {@code accepted} 与 {@code rejected} 分开返回：</b>
 * 事件上报是"尽力而为"的通道，部分条目被拒不应导致整批失败，但**必须让上报方知道**。
 * 只回一个 200，客户端会以为全成功了，于是"事件丢了一半"这件事永远不会被发现 ——
 * 而行为事件是产品分析的输入，静默丢失会直接变成错误的结论。
 */
@Getter
@Schema(name = "ClientEventReportResultVO", description = "批量上报结果")
public class ClientEventReportResultVO {

    @Schema(description = "成功落库的条数")
    private final int accepted;

    @Schema(description = "被拒绝的条数（事件名为空 / 长度超限 / 格式非法等）")
    private final int rejected;

    public ClientEventReportResultVO(int accepted, int rejected) {
        this.accepted = accepted;
        this.rejected = rejected;
    }

}
