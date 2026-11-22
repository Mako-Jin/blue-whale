package com.bluewhale.alert.service.impl;

import com.bluewhale.alert.dto.AlertSilenceCreateRequest;
import com.bluewhale.alert.po.AlertSilencePo;
import com.bluewhale.alert.mapper.AlertSilenceMapper;
import com.bluewhale.alert.vo.AlertSilenceVO;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertSilenceServiceImpl} 静默匹配与参数校验单元测试。
 *
 * @author BlueWhale
 */
class AlertSilenceServiceImplTest {

    private final AlertSilenceMapper alertSilenceMapper = mock(AlertSilenceMapper.class);

    private final AlertSilenceServiceImpl silenceService =
            new AlertSilenceServiceImpl(alertSilenceMapper);

    @Test
    @DisplayName("isSilenced：规则、级别（忽略大小写）与关键字同时匹配时命中")
    void shouldMatchSilence() {
        when(alertSilenceMapper.selectList(any())).thenReturn(List.of(silence("cpu_high", "WARN", "92%")));

        assertTrue(silenceService.isSilenced("cpu_high", "warn", "CPU 使用率 92%"));
    }

    @Test
    @DisplayName("isSilenced：规则编码不匹配时不命中")
    void shouldNotMatchDifferentRule() {
        when(alertSilenceMapper.selectList(any())).thenReturn(List.of(silence("cpu_high", null, null)));

        assertFalse(silenceService.isSilenced("mem_high", "WARN", "内存 95%"));
    }

    @Test
    @DisplayName("isSilenced：关键字不在内容中时不命中")
    void shouldNotMatchKeyword() {
        when(alertSilenceMapper.selectList(any())).thenReturn(List.of(silence(null, null, "磁盘")));

        assertFalse(silenceService.isSilenced("cpu_high", "WARN", "CPU 使用率 92%"));
    }

    @Test
    @DisplayName("isSilenced：无覆盖当前时间的静默时不命中")
    void shouldNotMatchWithoutActiveSilence() {
        when(alertSilenceMapper.selectList(any())).thenReturn(List.of());

        assertFalse(silenceService.isSilenced("cpu_high", "WARN", "CPU 使用率 92%"));
    }

    @Test
    @DisplayName("create：结束时间早于开始时间直接拒绝")
    void shouldRejectInvalidWindow() {
        AlertSilenceCreateRequest request = new AlertSilenceCreateRequest();
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().minusMinutes(1));

        assertThrows(BizException.class, () -> silenceService.create(request));
        verify(alertSilenceMapper, never()).insert(any(AlertSilencePo.class));
    }

    @Test
    @DisplayName("create：合法窗口写入静默规则并返回完整视图")
    void shouldCreateSilence() {
        AlertSilenceCreateRequest request = new AlertSilenceCreateRequest();
        request.setRuleCode("cpu_high");
        request.setAlertLevel("WARN");
        request.setKeyword("92%");
        request.setStartTime(LocalDateTime.of(2026, 11, 22, 10, 0));
        request.setEndTime(LocalDateTime.of(2026, 11, 22, 12, 0));
        request.setReason("发布窗口");
        request.setCreatedBy("admin");

        AlertSilenceVO vo = silenceService.create(request);

        assertEquals("cpu_high", vo.getRuleCode());
        assertEquals("WARN", vo.getAlertLevel());
        assertEquals("92%", vo.getKeyword());
        assertEquals(request.getStartTime(), vo.getStartTime());
        assertEquals(request.getEndTime(), vo.getEndTime());
        assertEquals("发布窗口", vo.getReason());
        assertEquals("admin", vo.getCreatedBy());
        verify(alertSilenceMapper).insert(any(AlertSilencePo.class));
    }

    @Test
    @DisplayName("delete：命中则删除，未命中抛 NOT_FOUND")
    void shouldDeleteSilence() {
        AlertSilencePo existing = silence("cpu_high", "WARN", null);
        when(alertSilenceMapper.selectById(1L)).thenReturn(existing);

        silenceService.delete(1L);
        verify(alertSilenceMapper).deleteById(1L);

        when(alertSilenceMapper.selectById(2L)).thenReturn(null);
        assertEquals(com.bluewhale.common.core.exception.ErrorCode.NOT_FOUND.getCode(),
                assertThrows(BizException.class, () -> silenceService.delete(2L)).getCode());
    }

    /**
     * 构造静默规则桩对象。
     *
     * @param ruleCode   规则编码
     * @param alertLevel 告警级别
     * @param keyword    内容关键字
     * @return 静默实体
     */
    private static AlertSilencePo silence(String ruleCode, String alertLevel, String keyword) {
        AlertSilencePo silence = new AlertSilencePo();
        silence.setId(1L);
        silence.setRuleCode(ruleCode);
        silence.setAlertLevel(alertLevel);
        silence.setKeyword(keyword);
        silence.setStartTime(LocalDateTime.now().minusHours(1));
        silence.setEndTime(LocalDateTime.now().plusHours(1));
        return silence;
    }
}
