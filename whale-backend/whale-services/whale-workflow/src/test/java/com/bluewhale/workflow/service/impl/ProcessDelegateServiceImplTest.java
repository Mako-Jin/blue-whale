package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.dto.DelegateRuleSaveRequest;
import com.bluewhale.workflow.entity.ProcessDelegate;
import com.bluewhale.workflow.mapper.ProcessDelegateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessDelegateServiceImpl} 代理规则校验与生效解析测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessDelegateServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Mock
    private ProcessDelegateMapper processDelegateMapper;

    @InjectMocks
    private ProcessDelegateServiceImpl processDelegateService;

    @Test
    @DisplayName("resolveActive：命中生效规则返回代理人")
    void shouldResolveActiveDelegate() {
        ProcessDelegate rule = new ProcessDelegate();
        rule.setToAccount("zhaoliu");
        when(processDelegateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));

        Optional<String> result = processDelegateService.resolveActive("wf", "manager", LocalDateTime.now());

        assertTrue(result.isPresent());
        assertEquals("zhaoliu", result.get());
    }

    @Test
    @DisplayName("resolveActive：入参为空不查库")
    void shouldReturnEmptyWhenInputBlank() {
        assertEquals(Optional.empty(), processDelegateService.resolveActive(null, "manager", LocalDateTime.now()));
        assertEquals(Optional.empty(), processDelegateService.resolveActive("wf", " ", LocalDateTime.now()));
    }

    @Test
    @DisplayName("create：结束时间不晚于开始时间抛 400")
    void shouldThrowWhenTimeRangeInvalid() {
        DelegateRuleSaveRequest request = request("2026-11-10T00:00", "2026-11-09T00:00");

        BizException ex = assertThrows(BizException.class, () -> processDelegateService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("create：委托人与代理人相同抛 400")
    void shouldThrowWhenSelfDelegation() {
        DelegateRuleSaveRequest request = request("2026-11-09T00:00", "2026-11-10T00:00");
        request.setToAccount("manager");

        BizException ex = assertThrows(BizException.class, () -> processDelegateService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("create：合法请求落库")
    void shouldCreateWhenValid() {
        DelegateRuleSaveRequest request = request("2026-11-09T00:00", "2026-11-10T00:00");

        processDelegateService.create(request);

        verify(processDelegateMapper).insert(any(ProcessDelegate.class));
    }

    /**
     * 构造保存请求。
     *
     * @param start 生效开始
     * @param end 生效结束
     * @return 保存请求
     */
    private static DelegateRuleSaveRequest request(String start, String end) {
        DelegateRuleSaveRequest request = new DelegateRuleSaveRequest();
        request.setDefinitionCode("wf");
        request.setFromAccount("manager");
        request.setToAccount("zhaoliu");
        request.setStartTime(LocalDateTime.parse(start));
        request.setEndTime(LocalDateTime.parse(end));
        request.setEnabled(true);
        return request;
    }
}
