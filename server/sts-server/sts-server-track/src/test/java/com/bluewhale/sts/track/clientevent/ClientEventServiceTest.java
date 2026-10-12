package com.bluewhale.sts.track.clientevent;

import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上报逻辑的单元测试 —— 不启 Spring，只测业务判断。
 *
 * <p>这几条用例守住的是**事件名校验策略**，而不是"代码能跑"：
 * 事件名是永久契约，一旦放宽（接受大写、接受无点分的单段名），
 * 历史数据就会被劈成多个近义名，且这个退化不会报错、只会让分析结果悄悄出错。
 * 策略一旦被人改掉，测试必须失败。
 */
@ExtendWith(MockitoExtension.class)
class ClientEventServiceTest {

    @Mock
    private ClientEventMapper clientEventMapper;

    private ClientEventService clientEventService;

    @BeforeEach
    void setUp() {
        clientEventService = new ClientEventService(clientEventMapper, new ObjectMapper());
    }

    /** 上下文是 ThreadLocal，不清理会串到下一个用例 —— 见 ClientLogServiceTest 的同类说明 */
    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    @DisplayName("事件名为空时逐条跳过并计数，不整批拒绝")
    void shouldRejectBlankEventNamePerItem() {
        ClientEventReportRequest request = request(
                item("order.submit", "{}"),
                item("", "空事件名"),
                item(null, "没有事件名"));

        when(clientEventMapper.insertBatch(anyList())).thenReturn(1);

        ClientEventReportResultVO result = clientEventService.report(request, "127.0.0.1");

        assertThat(result.getAccepted()).isEqualTo(1);
        assertThat(result.getRejected()).isEqualTo(2);
    }

    @Test
    @DisplayName("大写事件名被逐条拒绝：不做大小写兜底，拼错必须暴露而不是被悄悄纠正")
    void shouldRejectUpperCaseEventName() {
        ClientEventReportRequest request = request(
                item("order.submit", "合法"),
                item("Order.Submit", "大写"));
        when(clientEventMapper.insertBatch(anyList())).thenReturn(1);

        ClientEventReportResultVO result = clientEventService.report(request, "127.0.0.1");

        assertThat(result.getAccepted()).isEqualTo(1);
        assertThat(result.getRejected()).isEqualTo(1);
    }

    @Test
    @DisplayName("无点分的单段名被拒绝：至少两段才能表达归属，否则会出现一堆近义名")
    void shouldRejectNameWithoutDot() {
        ClientEventReportRequest request = request(
                item("order.submit", "合法"),
                item("submit", "缺段"),
                item("order.", "尾点无段"),
                item(".submit", "首点无段"));
        when(clientEventMapper.insertBatch(anyList())).thenReturn(1);

        ClientEventReportResultVO result = clientEventService.report(request, "127.0.0.1");

        assertThat(result.getAccepted()).isEqualTo(1);
        assertThat(result.getRejected()).isEqualTo(3);
    }

    @Test
    @DisplayName("整批条目事件名都不合法时不调用 insertBatch")
    void shouldNotTouchDatabaseWhenNoValidEvent() {
        ClientEventReportRequest request = request(item("Nope", "x"));

        ClientEventReportResultVO result = clientEventService.report(request, "127.0.0.1");

        assertThat(result.getAccepted()).isZero();
        assertThat(result.getRejected()).isEqualTo(1);
        // 空集合会让 <foreach> 生成残缺 SQL，所以这里必须一次都不要调用
        verify(clientEventMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("合法事件名逐条通过：大小写、点分、多段与下划线都应接受")
    void shouldAcceptWellFormedEventNames() {
        assertThat(ClientEventService.isValidEventName("order.submit")).isTrue();
        assertThat(ClientEventService.isValidEventName("page.view")).isTrue();
        assertThat(ClientEventService.isValidEventName("order.submit_v2")).isTrue();
        assertThat(ClientEventService.isValidEventName("a.b.c")).isTrue();

        // 反面：单段、大写、空、超长、非法起点
        assertThat(ClientEventService.isValidEventName("submit")).isFalse();
        assertThat(ClientEventService.isValidEventName("Order.Submit")).isFalse();
        assertThat(ClientEventService.isValidEventName("")).isFalse();
        assertThat(ClientEventService.isValidEventName(null)).isFalse();
        assertThat(ClientEventService.isValidEventName("order." + "x".repeat(200))).isFalse();
        assertThat(ClientEventService.isValidEventName("1order.submit")).isFalse();
        assertThat(ClientEventService.isValidEventName("order.submit-now")).isFalse();
    }

    @Test
    @DisplayName("props 以 JSON 落库，条目级元数据缺省回落到信封级")
    void shouldSerializePropsAndFallBackMetadata() {
        ClientEventItem item = item("order.submit", "带属性");
        item.setProps(Map.of("orderId", "o-1", "amount", 9));
        ClientEventReportRequest request = request(item);
        request.setSessionId("envelope-session");
        request.setRelease("9.9.9");
        when(clientEventMapper.insertBatch(anyList())).thenReturn(1);

        clientEventService.report(request, "127.0.0.1");

        ArgumentCaptor<List<ClientEventEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientEventMapper).insertBatch(captor.capture());
        ClientEventEntity saved = captor.getValue().get(0);
        assertThat(saved.getPropsJson()).contains("\"orderId\":\"o-1\"");
        // 实体属性名是 appRelease（列名 app_release），不是 release —— 见 ClientEventEntity 注释
        assertThat(saved.getAppRelease()).isEqualTo("9.9.9");
        assertThat(saved.getSessionId()).isEqualTo("envelope-session");
    }

    @Test
    @DisplayName("归属取会话租户，**不取**上报体里的 userId —— 那个值可伪造")
    void shouldTakeTenantFromSessionNotFromReportedUserId() {
        AuthContext context = new AuthContext();
        context.setTenantId(42L);
        AuthContextHolder.set(context);
        try {
            // 上报方声明自己是"用户 999" —— 属于别的租户的一个合法用户
            ClientEventItem item = item("order.submit", "伪造身份");
            item.setUserId("999");
            ClientEventReportRequest request = request(item);
            when(clientEventMapper.insertBatch(anyList())).thenReturn(1);

            clientEventService.report(request, "127.0.0.1");

            ArgumentCaptor<List<ClientEventEntity>> captor = ArgumentCaptor.forClass(List.class);
            verify(clientEventMapper).insertBatch(captor.capture());
            ClientEventEntity saved = captor.getValue().get(0);
            // userId 原样保留（它是产品分析的维度），但**归属与它无关**
            assertThat(saved.getUserId()).isEqualTo("999");
            assertThat(saved.getTenantId())
                    .as("若这里等于 999 所属的租户，说明有人拿上报体里的 userId 当了归属依据")
                    .isEqualTo(42L);
        } finally {
            AuthContextHolder.clear();
        }
    }

    @Test
    @DisplayName("登录前上报（无会话）归属为空，但照样入库")
    void shouldAcceptAnonymousReportWithNullTenant() {
        AuthContextHolder.clear();
        ClientEventReportRequest request = request(item("page.view", "登录前的浏览"));
        when(clientEventMapper.insertBatch(anyList())).thenReturn(1);

        ClientEventReportResultVO result = clientEventService.report(request, "127.0.0.1");

        assertThat(result.getAccepted()).isEqualTo(1);
        ArgumentCaptor<List<ClientEventEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientEventMapper).insertBatch(captor.capture());
        assertThat(captor.getValue().get(0).getTenantId()).isNull();
    }

    private static ClientEventReportRequest request(ClientEventItem... items) {
        ClientEventReportRequest request = new ClientEventReportRequest();
        request.setApp("unit-test");
        request.setEvents(List.of(items));
        return request;
    }

    private static ClientEventItem item(String eventName, String note) {
        ClientEventItem item = new ClientEventItem();
        item.setEventName(eventName);
        item.setEventId("evt-" + note);
        item.setDeviceId("device-1");
        item.setTimestamp(1759999999999L);
        return item;
    }

}
