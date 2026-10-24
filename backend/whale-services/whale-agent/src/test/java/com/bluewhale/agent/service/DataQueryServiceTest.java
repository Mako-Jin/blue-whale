package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 只读数据服务单测：行数裁剪、脱敏、未启用与执行失败。
 *
 * @author BlueWhale
 */
class DataQueryServiceTest {

    @Test
    @DisplayName("查询成功：附执行 SQL、裁剪到上限并脱敏")
    void shouldQueryAndMask() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phone", "13812345678");
            row.put("fact_count", index);
            rows.add(row);
        }
        doReturn(rows).when(jdbc).queryForList(anyString());

        Map<String, Object> result = new DataQueryService(enabledProperties(), jdbc)
                .query("select * from v_ai_agent_fact_summary");

        assertThat(result.get("count")).isEqualTo(200);
        assertThat(result.get("truncated")).isEqualTo(true);
        assertThat((String) result.get("sql")).endsWith("LIMIT 200");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> masked = (List<Map<String, Object>>) result.get("rows");
        assertThat(masked.get(0).get("phone")).isEqualTo("***");
        assertThat(masked.get(0).get("fact_count")).isEqualTo(0);
    }

    @Test
    @DisplayName("未启用：拒绝")
    void shouldRejectWhenDisabled() {
        AgentProperties properties = new AgentProperties();

        assertThatThrownBy(() -> new DataQueryService(properties, mock(JdbcTemplate.class))
                .query("select * from v_ai_agent_fact_summary"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    @DisplayName("执行失败：转业务异常，不暴露底层细节")
    void shouldWrapDataAccessException() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new DataAccessResourceFailureException("down")).when(jdbc).queryForList(anyString());

        assertThatThrownBy(() -> new DataQueryService(enabledProperties(), jdbc)
                .query("select * from v_ai_agent_fact_summary"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("查询失败");
    }

    /**
     * 构造已启用只读通道的配置。
     *
     * @return 配置
     */
    private static AgentProperties enabledProperties() {
        AgentProperties properties = new AgentProperties();
        properties.getData().setEnabled(true);
        properties.getData().setUrl("jdbc:mysql://127.0.0.1:3306/bluewhale_agent");
        return properties;
    }
}
