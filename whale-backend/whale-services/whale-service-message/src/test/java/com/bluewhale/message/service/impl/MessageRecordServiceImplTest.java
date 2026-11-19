package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.message.dto.MessageRecordQueryRequest;
import com.bluewhale.message.po.MessageRecordPo;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageRecordServiceImpl} 查询条件单元测试。
 *
 * @author BlueWhale
 */
class MessageRecordServiceImplTest {

    private final MessageRecordMapper recordMapper = mock(MessageRecordMapper.class);

    private final MessageDeliveryMapper deliveryMapper = mock(MessageDeliveryMapper.class);

    private final MessageRecordServiceImpl service = new MessageRecordServiceImpl(recordMapper, deliveryMapper);

    /**
     * Lambda 条件构造器依赖实体表信息缓存，纯单测需手动初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MessageRecordPo.class);
    }

    @Test
    @DisplayName("非法渠道直接拒绝，不拼入 SQL")
    void shouldRejectUnknownChannel() {
        MessageRecordQueryRequest request = new MessageRecordQueryRequest();
        request.setChannel("EMAIL OR 1=1");

        assertThrows(BizException.class, () -> service.page(request));
        verify(recordMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("渠道过滤使用 FIND_IN_SET 精确 token 匹配")
    void shouldUseFindInSetForChannel() {
        when(recordMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        MessageRecordQueryRequest request = new MessageRecordQueryRequest();
        request.setChannel("im");

        service.page(request);

        ArgumentCaptor<Wrapper<MessageRecordPo>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(recordMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("FIND_IN_SET"), "渠道过滤应走 FIND_IN_SET");
        assertFalse(sql.contains("LIKE"), "渠道过滤不应使用 LIKE");
    }
}
