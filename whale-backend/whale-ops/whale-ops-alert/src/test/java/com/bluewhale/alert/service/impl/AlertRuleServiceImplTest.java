package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.dto.AlertRuleQueryRequest;
import com.bluewhale.alert.dto.AlertRuleSaveRequest;
import com.bluewhale.alert.po.AlertRulePo;
import com.bluewhale.alert.mapper.AlertRuleMapper;
import com.bluewhale.alert.vo.AlertRuleVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertRuleServiceImpl} 分页、详情、新增查重、更新与启停单元测试。
 *
 * @author BlueWhale
 */
class AlertRuleServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    private final AlertRuleMapper alertRuleMapper = mock(AlertRuleMapper.class);

    private final AlertRuleServiceImpl alertRuleService = new AlertRuleServiceImpl(alertRuleMapper);

    /**
     * 构造新增 / 更新请求桩对象。
     *
     * @param ruleCode 规则编码
     * @param ruleName 规则名称
     * @return 规则保存请求
     */
    private static AlertRuleSaveRequest saveRequest(String ruleCode, String ruleName) {
        AlertRuleSaveRequest request = new AlertRuleSaveRequest();
        request.setRuleCode(ruleCode);
        request.setRuleName(ruleName);
        request.setMetric("cpu.usage");
        request.setLevel("WARN");
        request.setStatus(1);
        return request;
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        AlertRulePo entity = new AlertRulePo();
        entity.setId(1L);
        entity.setRuleCode("cpu_high");
        entity.setRuleName("CPU 使用率过高");
        entity.setStatus(1);
        Page<AlertRulePo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(alertRuleMapper.selectPage(ArgumentMatchers.<Page<AlertRulePo>>any(),
                ArgumentMatchers.<Wrapper<AlertRulePo>>any())).thenReturn(page);

        AlertRuleQueryRequest request = new AlertRuleQueryRequest();
        request.setKeyword("cpu");
        request.setStatus(1);
        PageResult<AlertRuleVO> result = alertRuleService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("cpu_high", result.getRecords().get(0).getRuleCode());
        assertEquals("CPU 使用率过高", result.getRecords().get(0).getRuleName());
    }

    @Test
    @DisplayName("getDetail：规则不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(alertRuleMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> alertRuleService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("create：落库并返回规则视图对象")
    void shouldCreateRule() {
        when(alertRuleMapper.selectCount(ArgumentMatchers.<Wrapper<AlertRulePo>>any())).thenReturn(0L);
        AlertRulePo saved = new AlertRulePo();
        saved.setId(100L);
        saved.setRuleCode("disk_high");
        saved.setRuleName("磁盘使用率过高");
        when(alertRuleMapper.selectById(any())).thenReturn(saved);

        AlertRuleVO vo = alertRuleService.create(saveRequest("disk_high", "磁盘使用率过高"));

        assertEquals("disk_high", vo.getRuleCode());
        verify(alertRuleMapper).insert(any(AlertRulePo.class));
    }

    @Test
    @DisplayName("create：rule_code 冲突抛参数错误且不落库")
    void shouldRejectDuplicateCreate() {
        when(alertRuleMapper.selectCount(ArgumentMatchers.<Wrapper<AlertRulePo>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> alertRuleService.create(saveRequest("cpu_high", "CPU 使用率过高")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(alertRuleMapper, never()).insert(any(AlertRulePo.class));
    }

    @Test
    @DisplayName("update：校验唯一后更新并返回视图对象")
    void shouldUpdateRule() {
        AlertRulePo existing = new AlertRulePo();
        existing.setId(8L);
        existing.setRuleCode("cpu_high");
        existing.setRuleName("旧名称");
        when(alertRuleMapper.selectById(8L)).thenReturn(existing);
        when(alertRuleMapper.selectCount(ArgumentMatchers.<Wrapper<AlertRulePo>>any())).thenReturn(0L);

        AlertRuleVO vo = alertRuleService.update(8L, saveRequest("cpu_high", "新名称"));

        assertEquals("新名称", vo.getRuleName());
        verify(alertRuleMapper).updateById(any(AlertRulePo.class));
    }

    @Test
    @DisplayName("update：规则不存在抛 NOT_FOUND 且不更新")
    void shouldThrowWhenUpdateMissing() {
        when(alertRuleMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> alertRuleService.update(99L, saveRequest("cpu_high", "新名称")));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(alertRuleMapper, never()).updateById(any(AlertRulePo.class));
    }

    @Test
    @DisplayName("updateStatus：设置状态后落库")
    void shouldUpdateStatus() {
        AlertRulePo existing = new AlertRulePo();
        existing.setId(8L);
        existing.setStatus(1);
        when(alertRuleMapper.selectById(8L)).thenReturn(existing);

        alertRuleService.updateStatus(8L, 0);

        assertEquals(0, existing.getStatus());
        verify(alertRuleMapper).updateById(any(AlertRulePo.class));
    }

    @Test
    @DisplayName("updateStatus：非法状态抛参数错误且不落库")
    void shouldRejectInvalidStatus() {
        BizException ex = assertThrows(BizException.class, () -> alertRuleService.updateStatus(8L, 2));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(alertRuleMapper, never()).updateById(any(AlertRulePo.class));
    }
}
