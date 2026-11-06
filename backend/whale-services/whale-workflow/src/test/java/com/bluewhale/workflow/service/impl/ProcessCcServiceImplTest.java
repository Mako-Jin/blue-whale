package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.dto.ProcessCcQueryRequest;
import com.bluewhale.workflow.entity.ProcessCc;
import com.bluewhale.workflow.mapper.ProcessCcMapper;
import com.bluewhale.workflow.vo.ProcessCcVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessCcServiceImpl} 抄送查询与按 ID 查询测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessCcServiceImplTest {

    @Mock
    private ProcessCcMapper processCcMapper;

    @InjectMocks
    private ProcessCcServiceImpl processCcService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProcessCc.class);
    }

    @Test
    @DisplayName("page：候选收件人 trim/去重后按 IN 查询")
    void shouldQueryByNormalizedCandidates() {
        ProcessCcQueryRequest query = new ProcessCcQueryRequest();
        query.setRecipientCandidates(List.of("u1", " 42 ", "u1", " ", "u2"));
        ProcessCc cc = new ProcessCc();
        cc.setId(1L);
        cc.setRecipient("u1");
        Page<ProcessCc> page = new Page<>(1, 10, false);
        page.setRecords(List.of(cc));
        page.setTotal(1);
        when(processCcMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        List<ProcessCcVO> records = processCcService.page(query).getRecords();

        ArgumentCaptor<LambdaQueryWrapper<ProcessCc>> captor = wrapperCaptor();
        verify(processCcMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("recipient IN"));
        Collection<Object> values = captor.getValue().getParamNameValuePairs().values();
        assertTrue(values.contains("u1"));
        assertTrue(values.contains("42"));
        assertTrue(values.contains("u2"));
        assertFalse(values.contains(" 42 "));
        assertEquals(1, records.size());
    }

    @Test
    @DisplayName("page：候选为空时回退单值收件人查询")
    void shouldFallbackToSingleRecipient() {
        ProcessCcQueryRequest query = new ProcessCcQueryRequest();
        query.setRecipient("42");
        Page<ProcessCc> page = new Page<>(1, 10, false);
        page.setRecords(List.of());
        page.setTotal(0);
        when(processCcMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        processCcService.page(query);

        ArgumentCaptor<LambdaQueryWrapper<ProcessCc>> captor = wrapperCaptor();
        verify(processCcMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("recipient ="));
        assertTrue(captor.getValue().getParamNameValuePairs().values().contains("42"));
    }

    @Test
    @DisplayName("get：存在时返回抄送视图")
    void shouldGetCcById() {
        ProcessCc cc = new ProcessCc();
        cc.setId(7L);
        cc.setInstanceId(100L);
        cc.setRecipient("u1");
        cc.setReadFlag(0);
        cc.setCreateTime(LocalDateTime.now());
        when(processCcMapper.selectById(7L)).thenReturn(cc);

        ProcessCcVO vo = processCcService.get(7L);

        assertEquals(7L, vo.getId());
        assertEquals(100L, vo.getInstanceId());
    }

    @Test
    @DisplayName("get：不存在抛 404")
    void shouldThrowWhenCcMissing() {
        when(processCcMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> processCcService.get(9L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(processCcMapper, never()).insert(any(ProcessCc.class));
    }

    /**
     * 构造 LambdaQueryWrapper 捕获器。
     *
     * @return 捕获器
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<LambdaQueryWrapper<ProcessCc>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }
}
