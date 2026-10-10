package com.bluewhale.sts.track.clientevent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量上报请求。
 *
 * <p><b>为什么是"批量 + 信封"而不是"一条一个请求"：</b>
 * 一条一个请求意味着事件量直接等于请求量 —— 用户数 × 操作频次，
 * 后端会被自己的客户端打挂。批量把请求数降一到两个数量级，
 * 也是前端能做采样、去重、脱敏的前提（攒够一批才谈得上这些）。
 *
 * <p><b>信封里放 {@code app} / {@code release} / {@code sessionId}：</b>
 * 同一批事件必然来自同一个应用、同一个版本、同一次会话，逐条携带是纯粹的重复。
 * 逐条仍可携带，逐条的优先 —— 这样既省带宽，
 * 也不阻止少数条目标注不同的来源。
 *
 * <p>校验分两层：本类上的注解管**信封**，任何一条不合格就整体拒绝（这是客户端的编程错误，
 * 应该立刻暴露）；单条事件的合法性由 {@link ClientEventService} 逐条判断（这是数据质量问题，
 * 不该让一条畸形事件拖垮整批）。
 */
@Getter
@Setter
@Schema(name = "ClientEventReportRequest", description = "客户端行为事件批量上报请求")
public class ClientEventReportRequest {

    @NotBlank(message = "app 不能为空")
    @Size(max = 64, message = "app 长度不能超过 64")
    @Schema(description = "上报方应用标识", example = "platform-web", requiredMode = Schema.RequiredMode.REQUIRED)
    private String app;

    @Size(max = 64, message = "release 长度不能超过 64")
    @Schema(description = "应用版本 / 构建号", example = "0.1.0")
    private String release;

    @Size(max = 64, message = "sessionId 长度不能超过 64")
    @Schema(description = "会话标识，批次级默认值")
    private String sessionId;

    /**
     * 单批上限。
     *
     * <p>定这个上限不是为了"防大请求"，而是为了让**客户端的失败模式可预期**：
     * 超过上限会得到明确的 40000，而不是一个被网关截断、看起来像网络故障的响应。
     */
    @NotEmpty(message = "events 不能为空")
    @Size(max = 500, message = "单批最多上报 500 条")
    @Valid
    @Schema(description = "事件条目，单批最多 500 条", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ClientEventItem> events;

}
