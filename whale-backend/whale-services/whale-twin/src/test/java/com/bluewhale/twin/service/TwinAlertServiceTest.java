package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.dto.TwinAlertVO;
import com.bluewhale.twin.entity.TwinAlert;
import com.bluewhale.twin.mapper.TwinAlertMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阈值告警服务单测：落库口径（恢复回填/新级别收口旧行）与查询。
 *
 * @author BlueWhale
 */
class TwinAlertServiceTest {

    private static final Long SCENE_ID = 100L;

    private static final Long TENANT_ID = 1L;

    private TwinAlertMapper alertMapper;

    private TwinAlertService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TwinAlert.class);
    }

    @BeforeEach
    void setUp() {
        alertMapper = mock(TwinAlertMapper.class);
        service = new TwinAlertService(alertMapper);
    }

    @Test
    void recordInsertsRowForNonNormalLevel() {
        when(alertMapper.update(any(), any())).thenReturn(1);
        when(alertMapper.insert(any(TwinAlert.class))).thenAnswer(invocation -> {
            TwinAlert entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });

        service.record(SCENE_ID, TENANT_ID, alert("critical"));

        // 先收口旧未恢复行，再插新行
        verify(alertMapper).update(any(), any());
        verify(alertMapper).insert(any(TwinAlert.class));
    }

    @Test
    void recordNormalLevelOnlyResolvesWithoutInsert() {
        when(alertMapper.update(any(), any())).thenReturn(1);

        service.record(SCENE_ID, TENANT_ID, alert("normal"));

        verify(alertMapper).update(any(), any());
        verify(alertMapper, never()).insert(any(TwinAlert.class));
    }

    @Test
    void listMapsRowsToVo() {
        TwinAlert row = new TwinAlert();
        row.setId(9L);
        row.setEntityId(200L);
        row.setEntityCode("boiler-01");
        row.setBindingId(1L);
        row.setStateKey("color");
        row.setAlertValue("82.5");
        row.setPreviousLevel("warn");
        row.setLevel("critical");
        row.setThresholdsJson("[50,80]");
        row.setResolved(0);
        row.setCreateTime(LocalDateTime.of(2026, 11, 10, 16, 0));
        when(alertMapper.selectList(any())).thenReturn(List.of(row));

        List<TwinAlertVO> result = service.list(SCENE_ID, null, null, null, null);

        assertEquals(1, result.size());
        TwinAlertVO vo = result.get(0);
        assertEquals("9", vo.getId());
        assertEquals("critical", vo.getLevel());
        assertEquals("warn", vo.getPreviousLevel());
        assertEquals(Boolean.FALSE, vo.getResolved());
        assertNotNull(vo.getThresholds());
        assertEquals(2, vo.getThresholds().size());
        assertEquals(new BigDecimal("80"), vo.getThresholds().get(1));
        assertTrue(vo.getCreateTime().startsWith("2026-11-10T16:00"));
    }

    @Test
    void listResolvesFilterPassedThrough() {
        when(alertMapper.selectList(any())).thenReturn(List.of());

        List<TwinAlertVO> unresolved = service.list(SCENE_ID, null, false, null, 10);
        List<TwinAlertVO> capped = service.list(SCENE_ID, "critical", null, null, 500);

        assertNotNull(unresolved);
        assertNotNull(capped);
        // 服务在 mapper 层做 LIMIT 截断（200 上限由 service 收口），这里仅验证不抛错
    }

    @Test
    void listMapsAckedState() {
        TwinAlert row = new TwinAlert();
        row.setId(9L);
        row.setBindingId(1L);
        row.setStateKey("color");
        row.setLevel("critical");
        row.setResolved(0);
        row.setAck(1);
        row.setAckTime(LocalDateTime.of(2026, 11, 10, 17, 0));
        row.setAckBy(3L);
        row.setCreateTime(LocalDateTime.of(2026, 11, 10, 16, 0));
        when(alertMapper.selectList(any())).thenReturn(List.of(row));

        var result = service.list(SCENE_ID, null, null, true, 10);

        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.get(0).getAcked());
        assertTrue(result.get(0).getAckTime().startsWith("2026-11-10T17:00"));
        assertEquals("3", result.get(0).getAckBy());
    }

    @Test
    void ackMarksRowAndReturnsVo() {
        TwinAlert row = new TwinAlert();
        row.setId(9L);
        row.setBindingId(1L);
        row.setStateKey("color");
        row.setLevel("warn");
        row.setResolved(0);
        row.setAck(0);
        when(alertMapper.selectOne(any())).thenReturn(row);
        when(alertMapper.update(any(), any())).thenReturn(1);

        TwinAlertVO vo = service.ack(SCENE_ID, 9L, 3L);

        assertEquals(Boolean.TRUE, vo.getAcked());
        assertEquals("3", vo.getAckBy());
        assertNotNull(vo.getAckTime());
        verify(alertMapper).update(any(), any());
    }

    @Test
    void ackRejectsUnknownAlert() {
        when(alertMapper.selectOne(any())).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> service.ack(SCENE_ID, 404L, 3L));

        assertEquals("告警不存在", exception.getMessage());
        verify(alertMapper, never()).update(any(), any());
    }

    @Test
    void ackRejectsDuplicateConfirmation() {
        TwinAlert row = new TwinAlert();
        row.setId(9L);
        row.setBindingId(1L);
        row.setStateKey("color");
        row.setLevel("warn");
        row.setAck(1);
        when(alertMapper.selectOne(any())).thenReturn(row);

        BizException exception = assertThrows(BizException.class,
                () -> service.ack(SCENE_ID, 9L, 3L));

        assertEquals("该告警已确认", exception.getMessage());
        verify(alertMapper, never()).update(any(), any());
    }

    @Test
    void listUnresolvedMapsRows() {
        TwinAlert row = new TwinAlert();
        row.setId(7L);
        row.setBindingId(1L);
        row.setStateKey("color");
        row.setLevel("warn");
        row.setResolved(0);
        row.setCreateTime(LocalDateTime.of(2026, 11, 10, 15, 0));
        when(alertMapper.selectList(any())).thenReturn(List.of(row));

        List<TwinAlertVO> result = service.listUnresolved(SCENE_ID);

        assertEquals(1, result.size());
        assertEquals("warn", result.get(0).getLevel());
        assertFalse(result.get(0).getResolved());
    }

    /**
     * 构造告警条目。
     *
     * @param level 级别
     * @return 告警视图
     */
    private static BindingAlertVO alert(String level) {
        return BindingAlertVO.builder()
                .bindingId("1")
                .entityId("200")
                .entityCode("boiler-01")
                .stateKey("color")
                .value(82.5)
                .previous("warn")
                .level(level)
                .thresholds(List.of(new BigDecimal("50"), new BigDecimal("80")))
                .ts("2026-11-10T16:00:00")
                .build();
    }
}
