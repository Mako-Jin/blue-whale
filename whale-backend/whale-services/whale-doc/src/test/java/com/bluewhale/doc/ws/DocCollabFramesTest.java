package com.bluewhale.doc.ws;

import com.bluewhale.doc.support.collab.DocCollabInit;
import com.bluewhale.doc.support.collab.DocCollabSnapshot;
import com.bluewhale.doc.support.collab.DocPresenceUser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocCollabFrames} 各帧 JSON 结构断言。
 *
 * @author BlueWhale
 */
class DocCollabFramesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final DocCollabFrames frames = new DocCollabFrames(mapper);

    private JsonNode parse(String json) {
        return mapper.readTree(json);
    }

    private static DocPresenceUser user(String clientId, String name) {
        DocPresenceUser user = new DocPresenceUser();
        user.setClientId(clientId);
        user.setName(name);
        user.setUserId(1L);
        user.setColor("#2563eb");
        return user;
    }

    @Test
    void readyShouldCarryInitStateAndPresence() {
        DocCollabInit init = new DocCollabInit(5L, "标题", 3L, "DRAFT", "{\"type\":\"doc\"}", List.of("u1"));
        JsonNode frame = parse(frames.ready(init, "client-1", "alice", true,
                List.of(user("client-1", "alice")), 12));

        assertThat(frame.get("type").asText()).isEqualTo("ready");
        assertThat(frame.get("pageId").asLong()).isEqualTo(5L);
        assertThat(frame.get("clientId").asText()).isEqualTo("client-1");
        assertThat(frame.get("username").asText()).isEqualTo("alice");
        assertThat(frame.get("title").asText()).isEqualTo("标题");
        assertThat(frame.get("revision").asLong()).isEqualTo(3L);
        assertThat(frame.get("status").asText()).isEqualTo("DRAFT");
        assertThat(frame.get("contentJson").asText()).isEqualTo("{\"type\":\"doc\"}");
        assertThat(frame.get("updates").isArray()).isTrue();
        assertThat(frame.get("updates").get(0).asText()).isEqualTo("u1");
        assertThat(frame.get("leader").asBoolean()).isTrue();
        assertThat(frame.get("snapshotSeconds").asInt()).isEqualTo(12);
        assertThat(frame.get("users").isArray()).isTrue();
        assertThat(frame.get("users").get(0).get("name").asText()).isEqualTo("alice");
        assertThat(frame.get("serverTime").asLong()).isPositive();
    }

    @Test
    void updateShouldCarryPayloadAndSender() {
        JsonNode frame = parse(frames.update("base64", "client-1"));

        assertThat(frame.get("type").asText()).isEqualTo("update");
        assertThat(frame.get("update").asText()).isEqualTo("base64");
        assertThat(frame.get("clientId").asText()).isEqualTo("client-1");
    }

    @Test
    void awarenessShouldCarryPayloadAndSender() {
        JsonNode frame = parse(frames.awareness("aware", "client-2"));

        assertThat(frame.get("type").asText()).isEqualTo("awareness");
        assertThat(frame.get("update").asText()).isEqualTo("aware");
        assertThat(frame.get("clientId").asText()).isEqualTo("client-2");
    }

    @Test
    void leaderShouldCarryFlag() {
        JsonNode leaderFrame = parse(frames.leader("client-1", true));
        assertThat(leaderFrame.get("type").asText()).isEqualTo("leader");
        assertThat(leaderFrame.get("clientId").asText()).isEqualTo("client-1");
        assertThat(leaderFrame.get("leader").asBoolean()).isTrue();

        JsonNode resign = parse(frames.leader("client-1", false));
        assertThat(resign.get("leader").asBoolean()).isFalse();
    }

    @Test
    void presenceShouldSerializeUsers() {
        JsonNode frame = parse(frames.presence(List.of(user("c1", "a"), user("c2", "b"))));

        assertThat(frame.get("type").asText()).isEqualTo("presence");
        assertThat(frame.get("users").size()).isEqualTo(2);
    }

    @Test
    void snapshotAckShouldFormatSavedAt() {
        DocCollabSnapshot snapshot = new DocCollabSnapshot(7L,
                LocalDateTime.of(2026, 1, 2, 3, 4, 5), false);
        JsonNode frame = parse(frames.snapshotAck(snapshot));

        assertThat(frame.get("type").asText()).isEqualTo("snapshot-ack");
        assertThat(frame.get("revision").asLong()).isEqualTo(7L);
        assertThat(frame.get("savedAt").asText()).isEqualTo("2026-01-02T03:04:05");
        assertThat(frame.get("skipped").asBoolean()).isFalse();
    }

    @Test
    void snapshotAckShouldAllowNullSavedAt() {
        JsonNode frame = parse(frames.snapshotAck(new DocCollabSnapshot(1L, null, true)));

        assertThat(frame.get("savedAt").isNull()).isTrue();
        assertThat(frame.get("skipped").asBoolean()).isTrue();
    }

    @Test
    void pongShouldCarryServerTime() {
        JsonNode frame = parse(frames.pong());

        assertThat(frame.get("type").asText()).isEqualTo("pong");
        assertThat(frame.get("serverTime").asLong()).isPositive();
    }

    @Test
    void errorShouldCarryCodeAndMessage() {
        JsonNode frame = parse(frames.error(40009, "非法帧"));

        assertThat(frame.get("type").asText()).isEqualTo("error");
        assertThat(frame.get("code").asInt()).isEqualTo(40009);
        assertThat(frame.get("message").asText()).isEqualTo("非法帧");
    }
}
