package com.bluewhale.oa.common.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.domain.repository.SequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SequenceAppServiceImpl} 业务单号生成单元测试。
 *
 * @author BlueWhale
 */
class SequenceAppServiceImplTest {

    private final SequenceRepository sequenceRepository = mock(SequenceRepository.class);

    private final SequenceAppServiceImpl sequenceAppService = new SequenceAppServiceImpl(sequenceRepository);

    @Test
    @DisplayName("nextCode：格式为 前缀 + yyyyMM + - + 三位序号")
    void shouldGenerateFormattedCode() {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        when(sequenceRepository.nextValue("LEAVE", period)).thenReturn(7L);

        String code = sequenceAppService.nextCode("LEAVE", "OA-LV-");

        assertEquals("OA-LV-" + period + "-007", code);
    }

    @Test
    @DisplayName("nextCode：业务类型为空时拒绝")
    void shouldRejectBlankBizType() {
        BizException exception = assertThrows(BizException.class,
                () -> sequenceAppService.nextCode(" ", "OA-LV-"));

        assertEquals(40005, exception.getCode());
    }

    @Test
    @DisplayName("nextCode：序号超过 999 自然扩位，不截断")
    void shouldGrowBeyondThreeDigits() {
        String period = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        when(sequenceRepository.nextValue("LEAVE", period)).thenReturn(1234L);

        String code = sequenceAppService.nextCode("LEAVE", "OA-LV-");

        assertTrue(code.endsWith("-1234"));
    }
}
