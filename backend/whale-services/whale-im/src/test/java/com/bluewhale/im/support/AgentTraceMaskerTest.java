package com.bluewhale.im.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 智能体轨迹脱敏单测：敏感键整值遮蔽、嵌套结构与字符串内赋值片段。
 *
 * @author BlueWhale
 */
class AgentTraceMaskerTest {

    @Test
    @DisplayName("敏感键整值遮蔽，普通字段保留")
    void shouldMaskSensitiveKeys() {
        Map<String, Object> source = Map.of(
                "name", "knowledge_search",
                "token", "abc-123",
                "nested", Map.of("password", "p@ss", "count", 2));

        @SuppressWarnings("unchecked")
        Map<String, Object> masked = (Map<String, Object>) AgentTraceMasker.mask(source);

        assertThat(masked.get("name")).isEqualTo("knowledge_search");
        assertThat(masked.get("token")).isEqualTo("***");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) masked.get("nested");
        assertThat(nested.get("password")).isEqualTo("***");
        assertThat(nested.get("count")).isEqualTo(2);
    }

    @Test
    @DisplayName("字符串内密钥赋值片段仅遮蔽值")
    void shouldMaskSecretAssignmentInsideText() {
        Map<String, Object> source = Map.of("message",
                "connection failed: password=abc123, host=db", "list", List.of("accessKey: k1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> masked = (Map<String, Object>) AgentTraceMasker.mask(source);

        assertThat(masked.get("message")).isEqualTo("connection failed: password=***, host=db");
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) masked.get("list");
        assertThat(list.get(0)).isEqualTo("accessKey=***");
    }

    @Test
    @DisplayName("非容器类型原样返回")
    void shouldKeepScalarValues() {
        assertThat(AgentTraceMasker.mask(null)).isNull();
        assertThat(AgentTraceMasker.mask(3)).isEqualTo(3);
        assertThat(AgentTraceMasker.mask("plain text")).isEqualTo("plain text");
    }
}
