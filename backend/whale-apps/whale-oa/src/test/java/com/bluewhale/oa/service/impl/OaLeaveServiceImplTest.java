package com.bluewhale.oa.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.dto.OaLeaveQueryRequest;
import com.bluewhale.oa.dto.OaLeaveSaveRequest;
import com.bluewhale.oa.entity.OaLeave;
import com.bluewhale.oa.mapper.OaLeaveMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OaLeaveServiceImpl} 请假申请状态机与校验单元测试。
 *
 * @author BlueWhale
 */
class OaLeaveServiceImplTest {

    private final OaLeaveMapper oaLeaveMapper = mock(OaLeaveMapper.class);

    private final OaLeaveServiceImpl oaLeaveService = new OaLeaveServiceImpl(oaLeaveMapper);

    @Test
    @DisplayName("create：单号唯一时落库且初始状态 PENDING")
    void shouldCreatePendingLeave() {
        when(oaLeaveMapper.selectCount(ArgumentMatchers.<Wrapper<OaLeave>>any())).thenReturn(0L);

        oaLeaveService.create(request());

        ArgumentCaptor<OaLeave> captor = ArgumentCaptor.forClass(OaLeave.class);
        verify(oaLeaveMapper).insert(captor.capture());
        OaLeave saved = captor.getValue();
        assertEquals("OA-LV-202610-100", saved.getCode());
        assertEquals("PENDING", saved.getStatus());
        assertEquals(new BigDecimal("2.0"), saved.getDays());
    }

    @Test
    @DisplayName("create：单号重复时拒绝")
    void shouldRejectDuplicateCode() {
        when(oaLeaveMapper.selectCount(ArgumentMatchers.<Wrapper<OaLeave>>any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> oaLeaveService.create(request()));

        assertEquals(400, exception.getCode());
        verify(oaLeaveMapper, never()).insert(ArgumentMatchers.<OaLeave>any());
    }

    @Test
    @DisplayName("create：结束日期早于开始日期时拒绝")
    void shouldRejectInvalidDateRange() {
        OaLeaveSaveRequest request = request();
        request.setEndDate(LocalDate.of(2026, 10, 20));

        BizException exception = assertThrows(BizException.class, () -> oaLeaveService.create(request));

        assertEquals(400, exception.getCode());
        verify(oaLeaveMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("updateStatus：PENDING 可通过审批")
    void shouldApprovePendingLeave() {
        OaLeave leave = pendingLeave();
        when(oaLeaveMapper.selectById(1L)).thenReturn(leave);

        oaLeaveService.updateStatus(1L, "APPROVED");

        ArgumentCaptor<OaLeave> captor = ArgumentCaptor.forClass(OaLeave.class);
        verify(oaLeaveMapper).updateById(captor.capture());
        assertEquals("APPROVED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("updateStatus：终态不可再次变更")
    void shouldRejectTerminalStatusChange() {
        OaLeave leave = pendingLeave();
        leave.setStatus("APPROVED");
        when(oaLeaveMapper.selectById(1L)).thenReturn(leave);

        BizException exception = assertThrows(BizException.class,
                () -> oaLeaveService.updateStatus(1L, "REJECTED"));

        assertEquals(400, exception.getCode());
        verify(oaLeaveMapper, never()).updateById(ArgumentMatchers.<OaLeave>any());
    }

    @Test
    @DisplayName("page：透传分页参数并映射视图对象")
    void shouldPageLeaves() {
        Page<OaLeave> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(pendingLeave()));
        when(oaLeaveMapper.selectPage(any(), ArgumentMatchers.<Wrapper<OaLeave>>any())).thenReturn(page);
        OaLeaveQueryRequest request = new OaLeaveQueryRequest();

        var result = oaLeaveService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals("OA-LV-202610-100", result.getRecords().getFirst().getCode());
    }

    /**
     * 构造新增请求。
     *
     * @return 新增请求
     */
    private static OaLeaveSaveRequest request() {
        OaLeaveSaveRequest request = new OaLeaveSaveRequest();
        request.setCode("OA-LV-202610-100");
        request.setApplicant("admin");
        request.setLeaveType("ANNUAL");
        request.setStartDate(LocalDate.of(2026, 10, 26));
        request.setEndDate(LocalDate.of(2026, 10, 27));
        request.setDays(new BigDecimal("2.0"));
        request.setReason("年假");
        return request;
    }

    /**
     * 构造待审批申请。
     *
     * @return 申请实体
     */
    private static OaLeave pendingLeave() {
        OaLeave leave = new OaLeave();
        leave.setId(1L);
        leave.setCode("OA-LV-202610-100");
        leave.setApplicant("admin");
        leave.setLeaveType("ANNUAL");
        leave.setStartDate(LocalDate.of(2026, 10, 26));
        leave.setEndDate(LocalDate.of(2026, 10, 27));
        leave.setDays(new BigDecimal("2.0"));
        leave.setStatus("PENDING");
        return leave;
    }
}
