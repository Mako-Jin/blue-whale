package com.bluewhale.sts.log.clientlog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 客户端日志出参。
 *
 * <p>{@code data} / {@code context} 以**结构化形态**返回（而不是库里的 JSON 字符串）：
 * 调用方拿到就能直接读字段。把解析放在服务端，既省掉每个消费方各写一遍 JSON.parse，
 * 也避免"某处解析失败静默返回空对象"这类分散的容错逻辑。
 *
 * <p>本对象**不复用实体类**：实体的字段跟着表结构走（含 dataJson / contextJson 这类存储细节），
 * 出参的字段跟着接口契约走。两者相等只是当前的巧合，一旦表加了列就会立刻分叉，
 * 而那时直接暴露实体的接口已经把这些列泄给了所有调用方。
 */
@Getter
@Setter
@Schema(name = "ClientLogVO", description = "客户端日志")
public class ClientLogVO {

    @Schema(description = "日志主键")
    private Long id;

    @Schema(description = "上报方应用标识")
    private String app;

    @Schema(description = "级别")
    private String level;

    @Schema(description = "命名空间")
    private String namespace;

    @Schema(description = "消息正文")
    private String message;

    @Schema(description = "随消息附带的原始数据")
    private List<Object> data;

    @Schema(description = "结构化上下文")
    private Map<String, Object> context;

    @Schema(description = "链路标识")
    private String traceId;

    @Schema(description = "会话标识")
    private String sessionId;

    @Schema(description = "触发日志时的路由")
    private String route;

    @Schema(description = "应用版本 / 构建号")
    private String release;

    @Schema(description = "客户端时间戳（毫秒），不可信，仅供展示")
    private Long timestamp;

    @Schema(description = "服务端接收时间戳（毫秒）")
    private Long serverTime;

    @Schema(description = "上报方 IP")
    private String ip;

}
