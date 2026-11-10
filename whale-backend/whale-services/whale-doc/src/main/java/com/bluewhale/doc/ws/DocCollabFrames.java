package com.bluewhale.doc.ws;

import com.bluewhale.doc.support.collab.DocCollabInit;
import com.bluewhale.doc.support.collab.DocCollabSnapshot;
import com.bluewhale.doc.support.collab.DocPresenceUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档协作 WS JSON 帧构造（M2）。
 *
 * <p>协议为文本 JSON，服务端不解析 Yjs 二进制内容，{@code update}/{@code awareness}
 * 字段均为 base64；客户端使用自定义 provider 处理 sync/seed/leader/snapshot。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocCollabFrames {

    private final ObjectMapper objectMapper;

    /**
     * 连接初始化帧。
     *
     * @param init            初始化数据
     * @param clientId        客户端ID
     * @param username        用户名
     * @param leader          是否 leader
     * @param users           在线用户
     * @param snapshotSeconds 快照间隔
     * @return 帧 JSON
     */
    public String ready(DocCollabInit init, String clientId, String username, boolean leader,
                        List<DocPresenceUser> users, int snapshotSeconds) {
        Map<String, Object> frame = base("ready");
        frame.put("pageId", init.pageId());
        frame.put("clientId", clientId);
        frame.put("username", username);
        frame.put("title", init.title());
        frame.put("revision", init.revision());
        frame.put("status", init.status());
        frame.put("contentJson", init.contentJson());
        frame.put("updates", init.updates());
        frame.put("leader", leader);
        frame.put("snapshotSeconds", snapshotSeconds);
        frame.put("users", users);
        frame.put("serverTime", System.currentTimeMillis());
        return json(frame);
    }

    /**
     * Yjs 更新帧。
     *
     * @param update   base64 更新
     * @param clientId 发送方客户端ID
     * @return 帧 JSON
     */
    public String update(String update, String clientId) {
        Map<String, Object> frame = base("update");
        frame.put("update", update);
        frame.put("clientId", clientId);
        return json(frame);
    }

    /**
     * Awareness 光标/在线状态帧。
     *
     * @param update   base64 awareness 更新
     * @param clientId 发送方客户端ID
     * @return 帧 JSON
     */
    public String awareness(String update, String clientId) {
        Map<String, Object> frame = base("awareness");
        frame.put("update", update);
        frame.put("clientId", clientId);
        return json(frame);
    }

    /**
     * Leader 变更帧。
     *
     * @param clientId leader 客户端ID
     * @param leader   是否 leader
     * @return 帧 JSON
     */
    public String leader(String clientId, boolean leader) {
        Map<String, Object> frame = base("leader");
        frame.put("clientId", clientId);
        frame.put("leader", leader);
        return json(frame);
    }

    /**
     * 在线用户帧。
     *
     * @param users 在线用户
     * @return 帧 JSON
     */
    public String presence(List<DocPresenceUser> users) {
        Map<String, Object> frame = base("presence");
        frame.put("users", users);
        return json(frame);
    }

    /**
     * 快照确认帧。
     *
     * @param snapshot 快照结果
     * @return 帧 JSON
     */
    public String snapshotAck(DocCollabSnapshot snapshot) {
        Map<String, Object> frame = base("snapshot-ack");
        frame.put("revision", snapshot.revision());
        frame.put("savedAt", snapshot.savedAt() == null ? null : snapshot.savedAt().toString());
        frame.put("skipped", snapshot.skipped());
        return json(frame);
    }

    /**
     * 心跳应答帧。
     *
     * @return 帧 JSON
     */
    public String pong() {
        Map<String, Object> frame = base("pong");
        frame.put("serverTime", System.currentTimeMillis());
        return json(frame);
    }

    /**
     * 错误帧。
     *
     * @param code    业务错误码
     * @param message 提示
     * @return 帧 JSON
     */
    public String error(int code, String message) {
        Map<String, Object> frame = base("error");
        frame.put("code", code);
        frame.put("message", message);
        return json(frame);
    }

    /**
     * 构造带 type 的基础帧。
     *
     * @param type 帧类型
     * @return 帧 Map
     */
    private Map<String, Object> base(String type) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        return frame;
    }

    /**
     * 序列化帧。
     *
     * @param frame 帧 Map
     * @return JSON 文本
     */
    private String json(Map<String, Object> frame) {
        return objectMapper.writeValueAsString(frame);
    }
}
