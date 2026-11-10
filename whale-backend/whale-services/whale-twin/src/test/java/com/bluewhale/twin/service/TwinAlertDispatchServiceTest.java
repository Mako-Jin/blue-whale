package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.twin.dto.TwinAlertDispatchVO;
import com.bluewhale.twin.entity.TwinAlertDispatch;
import com.bluewhale.twin.mapper.TwinAlertDispatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警投递记录服务单测：列宽截断与写入失败降级。
 *
 * @author BlueWhale
 */
class TwinAlertDispatchServiceTest {

    private TwinAlertDispatchMapper dispatchMapper;

    private TwinAlertDispatchService service;

    @BeforeEach
    void setUp() {
        dispatchMapper = mock(TwinAlertDispatchMapper.class);
        service = new TwinAlertDispatchService(dispatchMapper);
    }

    @Test
    void truncatesLongErrorAndTarget() {
        TwinAlertDispatch dispatch = new TwinAlertDispatch();
        dispatch.setErrorMsg("e".repeat(600));
        dispatch.setTarget("t".repeat(300));

        service.record(dispatch);

        ArgumentCaptor<TwinAlertDispatch> captor = ArgumentCaptor.forClass(TwinAlertDispatch.class);
        verify(dispatchMapper).insert(captor.capture());
        assertEquals(512, captor.getValue().getErrorMsg().length());
        assertEquals(256, captor.getValue().getTarget().length());
    }

    @Test
    void keepsShortFieldsUntouched() {
        TwinAlertDispatch dispatch = new TwinAlertDispatch();
        dispatch.setErrorMsg("boom");

        service.record(dispatch);

        assertEquals("boom", dispatch.getErrorMsg());
        assertNull(dispatch.getTarget());
    }

    @Test
    void swallowsInsertFailure() {
        when(dispatchMapper.insert(any(TwinAlertDispatch.class)))
                .thenThrow(new IllegalStateException("db down"));

        // 不抛异常：投递记录是旁路，不能连累告警主链路
        service.record(new TwinAlertDispatch());
    }

    @Test
    void listMapsRowsToVoWithStringIds() {
        TwinAlertDispatch row = new TwinAlertDispatch();
        row.setId(11L);
        row.setAlertId(22L);
        row.setSceneId(33L);
        row.setStateKey("temperature");
        row.setLevel("critical");
        row.setChannel(TwinAlertDispatchService.CHANNEL_WEBHOOK);
        row.setTarget("https://hook.example.com/a");
        row.setStatus(TwinAlertDispatchService.STATUS_SUCCESS);
        row.setMessageId(44L);
        row.setCreateTime(LocalDateTime.of(2026, 11, 10, 18, 30, 5));
        when(dispatchMapper.selectList(ArgumentMatchers.<Wrapper<TwinAlertDispatch>>any()))
                .thenReturn(List.of(row));

        List<TwinAlertDispatchVO> result = service.list(100L, "success", "webhook", 500);

        assertEquals(1, result.size());
        TwinAlertDispatchVO vo = result.get(0);
        assertEquals("11", vo.getId());
        assertEquals("22", vo.getAlertId());
        assertEquals("33", vo.getSceneId());
        assertEquals("critical", vo.getLevel());
        assertEquals("2026-11-10T18:30:05", vo.getCreateTime());
    }

    @Test
    void listReturnsEmptyWhenNoRows() {
        when(dispatchMapper.selectList(ArgumentMatchers.<Wrapper<TwinAlertDispatch>>any()))
                .thenReturn(List.of());

        assertTrue(service.list(100L, null, null, null).isEmpty());
    }
}
