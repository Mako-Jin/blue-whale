package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AlertRecordServiceImpl} 分页查询与字段映射单元测试。
 *
 * @author BlueWhale
 */
class AlertRecordServiceImplTest {

    private final AlertRecordMapper alertRecordMapper = mock(AlertRecordMapper.class);

    private final AlertRecordServiceImpl alertRecordService = new AlertRecordServiceImpl(alertRecordMapper);

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        AlertRecord entity = new AlertRecord();
        entity.setId(1L);
        entity.setRuleCode("cpu_high");
        entity.setAlertLevel("WARN");
        entity.setStatus("FIRING");
        entity.setContent("CPU 使用率 92% 超过阈值 80%");
        Page<AlertRecord> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(alertRecordMapper.selectPage(ArgumentMatchers.<Page<AlertRecord>>any(),
                ArgumentMatchers.<Wrapper<AlertRecord>>any())).thenReturn(page);

        AlertRecordQueryRequest request = new AlertRecordQueryRequest();
        request.setRuleCode("cpu_high");
        request.setAlertLevel("WARN");
        PageResult<AlertRecordVO> result = alertRecordService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        AlertRecordVO vo = result.getRecords().get(0);
        assertEquals("cpu_high", vo.getRuleCode());
        assertEquals("WARN", vo.getAlertLevel());
        assertEquals("FIRING", vo.getStatus());
    }

    @Test
    @DisplayName("page：单页条数收敛到上限 200")
    void shouldClampPageSize() {
        Page<AlertRecord> page = new Page<>(1L, 200L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(alertRecordMapper.selectPage(ArgumentMatchers.<Page<AlertRecord>>any(),
                ArgumentMatchers.<Wrapper<AlertRecord>>any())).thenReturn(page);

        AlertRecordQueryRequest request = new AlertRecordQueryRequest();
        request.setSize(9999L);
        PageResult<AlertRecordVO> result = alertRecordService.page(request);

        assertEquals(200L, result.getSize());
    }
}
