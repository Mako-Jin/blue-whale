package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.message.config.MessageReceiptProperties;
import com.bluewhale.message.po.MessageCallbackTaskPo;
import com.bluewhale.message.mapper.MessageCallbackTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageCallbackDispatcher} 多实例抢占逻辑单元测试。
 *
 * @author BlueWhale
 */
class MessageCallbackDispatcherTest {

    private final MessageCallbackTaskMapper callbackTaskMapper = mock(MessageCallbackTaskMapper.class);

    private final MessageReceiptProperties properties = new MessageReceiptProperties();

    private final MessageCallbackDispatcher dispatcher =
            new MessageCallbackDispatcher(callbackTaskMapper, properties);

    /**
     * Lambda 条件构造器依赖实体表信息缓存，纯单测需手动初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MessageCallbackTaskPo.class);
    }

    @Test
    @DisplayName("抢占失败的任务（已被其它实例处理）不重复派发")
    void shouldSkipTaskWhenClaimFails() {
        MessageCallbackTaskPo task = new MessageCallbackTaskPo();
        task.setId(9001L);
        task.setStatus("PENDING");
        when(callbackTaskMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<MessageCallbackTaskPo> page = invocation.getArgument(0);
            page.setRecords(List.of(task));
            page.setTotal(1L);
            return page;
        });
        when(callbackTaskMapper.claim(eq(9001L), anyString(), any())).thenReturn(0);

        dispatcher.dispatch();

        verify(callbackTaskMapper).claim(eq(9001L), anyString(), any());
        verify(callbackTaskMapper, never()).updateById(any(MessageCallbackTaskPo.class));
    }

    @Test
    @DisplayName("每次派发前回收抢占超时的任务")
    void shouldRecoverStaleTasksBeforeDispatch() {
        when(callbackTaskMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        dispatcher.dispatch();

        verify(callbackTaskMapper).recoverStale(any(), any());
    }
}
