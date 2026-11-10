package com.bluewhale.im.support;

import java.util.Optional;

/**
 * SSE 事件增量解析器（逐行喂入，遇空行产出完整事件）。
 *
 * <p>仅解析 {@code event:} 与 {@code data:} 两类字段；注释行（{@code :} 开头）忽略，
 * 未声明事件名时按 SSE 规范使用 {@code message}。</p>
 *
 * @author BlueWhale
 */
public final class SseEventParser {

    /** 默认事件名 */
    private static final String DEFAULT_EVENT = "message";

    private static final String FIELD_EVENT = "event:";

    private static final String FIELD_DATA = "data:";

    private String eventName;

    private final StringBuilder data = new StringBuilder();

    /**
     * 喂入一行文本。
     *
     * @param line 单行文本（不含换行符）
     * @return 空行时返回解析完成的事件；否则返回空
     */
    public Optional<SseEvent> accept(String line) {
        if (line == null || line.isEmpty()) {
            return flush();
        }
        if (line.startsWith(":")) {
            return Optional.empty();
        }
        if (line.startsWith(FIELD_EVENT)) {
            eventName = line.substring(FIELD_EVENT.length()).trim();
            return Optional.empty();
        }
        if (line.startsWith(FIELD_DATA)) {
            if (data.length() > 0) {
                data.append('\n');
            }
            data.append(line.substring(FIELD_DATA.length()).stripLeading());
        }
        return Optional.empty();
    }

    /**
     * 结束当前事件（流结束时补发未以空行结尾的事件）。
     *
     * @return 完整事件；无待发数据时返回空
     */
    public Optional<SseEvent> flush() {
        if (eventName == null && data.length() == 0) {
            return Optional.empty();
        }
        String name = eventName == null ? DEFAULT_EVENT : eventName;
        SseEvent event = new SseEvent(name, data.toString());
        eventName = null;
        data.setLength(0);
        return Optional.of(event);
    }

    /**
     * 解析完成的事件。
     *
     * @param name 事件名
     * @param data 事件数据（JSON 文本）
     */
    public record SseEvent(String name, String data) {
    }
}
