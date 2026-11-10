package com.bluewhale.agent.service;

import com.bluewhale.agent.dto.IndexMetaVO;
import com.bluewhale.agent.entity.AgentIndexMeta;
import com.bluewhale.agent.mapper.AgentIndexMetaMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 索引元数据服务单测：状态接口快照装配与数据库不可用回落（T-18 下沉）。
 *
 * @author BlueWhale
 */
class AgentIndexMetaServiceTest {

    private final AgentIndexMetaMapper mapper = mock(AgentIndexMetaMapper.class);

    private final AgentIndexMetaService service = new AgentIndexMetaService(mapper);

    @Test
    @DisplayName("snapshot 装配版本与构建时间")
    void shouldSnapshotMeta() {
        AgentIndexMeta meta = new AgentIndexMeta();
        meta.setId(1L);
        meta.setIndexVersion("v-20261031");
        meta.setLastBuildAt(LocalDateTime.of(2026, 10, 31, 21, 0, 0));
        when(mapper.selectById(1L)).thenReturn(meta);

        IndexMetaVO snapshot = service.snapshot();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getIndexVersion()).isEqualTo("v-20261031");
        assertThat(snapshot.getLastBuildAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 21, 0, 0));
    }

    @Test
    @DisplayName("数据库不可用时快照返回 null，不阻断状态接口")
    void shouldReturnNullWhenDatabaseUnavailable() {
        when(mapper.selectById(1L)).thenThrow(new RuntimeException("db down"));

        assertThat(service.snapshot()).isNull();
    }
}
