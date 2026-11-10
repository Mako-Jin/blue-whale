package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.FactVO;
import com.bluewhale.agent.entity.AgentFact;
import com.bluewhale.agent.mapper.AgentFactMapper;
import com.bluewhale.agent.support.facts.FactCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结构化事实仓储单测：视图装配下沉（T-18）后服务层返回完整 VO。
 *
 * @author BlueWhale
 */
class FactStoreServiceTest {

    private final AgentFactMapper factMapper = mock(AgentFactMapper.class);

    private final FactStoreService service = new FactStoreService(
            List.<FactCollector>of(), factMapper, new AgentProperties());

    @Test
    @DisplayName("queryVo 装配实体为视图对象：ID 字符串化、值解析、时间格式化")
    @SuppressWarnings("unchecked")
    void shouldAssembleFactVo() {
        AgentFact fact = new AgentFact();
        fact.setId(9007199254740993L);
        fact.setFactType("SERVICE");
        fact.setFactKey("whale-auth");
        fact.setValueJson("{\"port\":8081}");
        fact.setSourcePath("backend/whale-services/whale-service-auth");
        fact.setCapturedAt(LocalDateTime.of(2026, 10, 31, 12, 30, 15));
        when(factMapper.selectList(any())).thenReturn(List.of(fact));

        List<FactVO> result = service.queryVo("SERVICE", "whale-auth", 10, true);

        verify(factMapper).selectList(any());
        assertThat(result).hasSize(1);
        FactVO vo = result.get(0);
        assertThat(vo.getId()).isEqualTo("9007199254740993");
        assertThat(vo.getFactType()).isEqualTo("SERVICE");
        assertThat(vo.getFactKey()).isEqualTo("whale-auth");
        assertThat(vo.getValue()).isInstanceOf(java.util.Map.class);
        assertThat((java.util.Map<String, Object>) vo.getValue()).containsEntry("port", 8081);
        assertThat(vo.getSourcePath()).isEqualTo("backend/whale-services/whale-service-auth");
        assertThat(vo.getCapturedAt()).isEqualTo("2026-10-31T12:30:15");
    }

    @Test
    @DisplayName("queryVo 值非合法 JSON 时原样返回字符串")
    void shouldFallbackToRawValue() {
        AgentFact fact = new AgentFact();
        fact.setId(1L);
        fact.setFactType("COMMIT");
        fact.setFactKey("abc123");
        fact.setValueJson("not-a-json");
        when(factMapper.selectList(any())).thenReturn(List.of(fact));

        List<FactVO> result = service.queryVo("COMMIT", "abc123", 10, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo("not-a-json");
        assertThat(result.get(0).getCapturedAt()).isNull();
    }
}
