package com.bluewhale.twin.service;

import com.bluewhale.twin.entity.TwinAlertDispatch;
import com.bluewhale.twin.mapper.TwinAlertDispatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
