package com.bluewhale.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.entity.AgentOperation;
import com.bluewhale.agent.mapper.AgentOperationMapper;
import com.bluewhale.agent.support.ops.OperationInvoker;
import com.bluewhale.common.core.exception.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlatformOpService} 单元测试：创建、一次性确认、过期、越权与拒绝。
 *
 * @author BlueWhale
 */
class PlatformOpServiceTest {

    private static final long USER_ID = 7L;

    private static final long OTHER_USER_ID = 8L;

    private static final String CONFIRMATION_ID = "c-1";

    private static final String OPERATION = "form.definition.create";

    /**
     * 纯 Mockito 单测需手动初始化表信息缓存，否则 Lambda 条件解析列名失败。
     */
    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentOperation.class);
    }

    private final AgentOperationMapper mapper = mock(AgentOperationMapper.class);

    private final OperationInvoker invoker = mock(OperationInvoker.class);

    private PlatformOpService service;

    /**
     * 默认启用平台操作并指向本地目标。
     */
    @BeforeEach
    void setUp() {
        service = new PlatformOpService(enabledProperties(), mapper, invoker);
    }

    @Test
    @DisplayName("创建：生成 PENDING 令牌并返回确认卡片")
    void shouldCreatePendingToken() {
        when(mapper.insert(any(AgentOperation.class))).thenReturn(1);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", "demo");
        params.put("name", "示例表单");
        params.put("unknown", "丢弃");
        Map<String, Object> card = service.create(OPERATION, params, USER_ID, 100L);

        ArgumentCaptor<AgentOperation> captor = ArgumentCaptor.forClass(AgentOperation.class);
        verify(mapper).insert(captor.capture());
        AgentOperation record = captor.getValue();
        assertThat(record.getStatus()).isEqualTo("PENDING");
        assertThat(record.getUserId()).isEqualTo(USER_ID);
        assertThat(record.getPermission()).isEqualTo("sys:ai:ops:form");
        assertThat(record.getTarget()).isEqualTo("form");
        assertThat(record.getParamsJson()).contains("demo").doesNotContain("unknown");
        assertThat(record.getParamsHash()).hasSize(64);
        assertThat(card.get("confirmationId")).isEqualTo(record.getConfirmationId());
        assertThat(card.get("summary")).isEqualTo("创建表单定义：示例表单（demo）");
    }

    @Test
    @DisplayName("创建：未登记操作与缺少必填参数均拒绝")
    void shouldRejectUnknownOperationAndMissingParams() {
        assertThatThrownBy(() -> service.create("unknown.op", Map.of(), USER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登记");

        assertThatThrownBy(() -> service.create(OPERATION, Map.of("code", "demo"), USER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少必填参数");

        verify(mapper, never()).insert(any(AgentOperation.class));
    }

    @Test
    @DisplayName("未启用：创建与确认均拒绝")
    void shouldRejectWhenDisabled() {
        PlatformOpService disabled = new PlatformOpService(new AgentProperties(), mapper, invoker);

        assertThatThrownBy(() -> disabled.create(OPERATION, Map.of("code", "demo", "name", "示例"), USER_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未启用");
        assertThatThrownBy(() -> disabled.confirm(CONFIRMATION_ID, USER_ID, "sys:ai:ops:form"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    @DisplayName("确认成功：透传身份调用下游并回写状态")
    void shouldConfirmAndInvokeTarget() throws Exception {
        AgentOperation record = pendingRecord(USER_ID);
        when(mapper.selectOne(any())).thenReturn(record);
        when(mapper.update(any(), any())).thenReturn(1);
        when(invoker.invoke(anyString(), anyMap(), eq(USER_ID), eq("sys:agent:chat,sys:ai:ops:form")))
                .thenReturn(Map.of("id", "12"));

        Map<String, Object> result = service.confirm(CONFIRMATION_ID, USER_ID, "sys:agent:chat,sys:ai:ops:form");

        assertThat(result).containsEntry("id", "12");
        assertThat(record.getConfirmedBy()).isEqualTo(USER_ID);
        assertThat(record.getResultCode()).isZero();
        assertThat(record.getElapsedMs()).isNotNull();
        verify(invoker).invoke(eq("http://127.0.0.1:8084/form/v1/definition"),
                anyMap(), eq(USER_ID), eq("sys:agent:chat,sys:ai:ops:form"));
    }

    @Test
    @DisplayName("确认：非本人拒绝")
    void shouldRejectNonOwner() throws Exception {
        when(mapper.selectOne(any())).thenReturn(pendingRecord(USER_ID));

        assertThatThrownBy(() -> service.confirm(CONFIRMATION_ID, OTHER_USER_ID, "super_admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权确认");

        verify(invoker, never()).invoke(anyString(), anyMap(), any(), anyString());
    }

    @Test
    @DisplayName("确认：缺少操作权限拒绝")
    void shouldRejectWithoutPermission() throws Exception {
        when(mapper.selectOne(any())).thenReturn(pendingRecord(USER_ID));

        assertThatThrownBy(() -> service.confirm(CONFIRMATION_ID, USER_ID, "sys:agent:chat"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少操作权限");

        verify(invoker, never()).invoke(anyString(), anyMap(), any(), anyString());
    }

    @Test
    @DisplayName("确认：过期令牌置 EXPIRED 并拒绝")
    void shouldRejectExpiredToken() throws Exception {
        AgentOperation record = pendingRecord(USER_ID);
        record.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(mapper.selectOne(any())).thenReturn(record);
        when(mapper.update(any(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.confirm(CONFIRMATION_ID, USER_ID, "sys:ai:ops:form"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过期");

        verify(invoker, never()).invoke(anyString(), anyMap(), any(), anyString());
    }

    @Test
    @DisplayName("确认：一次性令牌第二次确认被拒")
    void shouldRejectSecondConfirmation() throws Exception {
        when(mapper.selectOne(any())).thenReturn(pendingRecord(USER_ID));
        when(mapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(CONFIRMATION_ID, USER_ID, "sys:ai:ops:form"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已使用");

        verify(invoker, never()).invoke(anyString(), anyMap(), any(), anyString());
    }

    @Test
    @DisplayName("确认：下游失败落 FAILED 并抛出")
    void shouldMarkFailedWhenInvokerFails() throws Exception {
        when(mapper.selectOne(any())).thenReturn(pendingRecord(USER_ID));
        when(mapper.update(any(), any())).thenReturn(1);
        when(invoker.invoke(anyString(), anyMap(), any(), anyString()))
                .thenThrow(new IOException("HTTP 500"));

        assertThatThrownBy(() -> service.confirm(CONFIRMATION_ID, USER_ID, "sys:ai:ops:form"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("执行失败");
    }

    @Test
    @DisplayName("拒绝：仅本人可拒绝，状态置 REJECTED")
    void shouldRejectToken() {
        when(mapper.selectOne(any())).thenReturn(pendingRecord(USER_ID));
        when(mapper.update(any(), any())).thenReturn(1);

        Map<String, Object> result = service.reject(CONFIRMATION_ID, USER_ID);

        assertThat(result).containsEntry("status", "REJECTED");
        assertThatThrownBy(() -> service.reject(CONFIRMATION_ID, OTHER_USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权确认");
    }

    /**
     * 构造启用平台操作的配置。
     *
     * @return 配置
     */
    private static AgentProperties enabledProperties() {
        AgentProperties properties = new AgentProperties();
        properties.getOps().setEnabled(true);
        return properties;
    }

    /**
     * 构造待确认记录。
     *
     * @param userId 发起用户ID
     * @return 记录
     */
    private static AgentOperation pendingRecord(long userId) {
        AgentOperation record = new AgentOperation();
        record.setId(1L);
        record.setConfirmationId(CONFIRMATION_ID);
        record.setUserId(userId);
        record.setOperation(OPERATION);
        record.setParamsJson("{\"code\":\"demo\",\"name\":\"示例表单\"}");
        record.setPermission("sys:ai:ops:form");
        record.setStatus("PENDING");
        record.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return record;
    }
}
