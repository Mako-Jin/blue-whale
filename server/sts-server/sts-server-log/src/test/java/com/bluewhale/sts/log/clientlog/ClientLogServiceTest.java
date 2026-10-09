package com.bluewhale.sts.log.clientlog;

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
 * <p>这几条用例守住的是**错误处理策略**，而不是"代码能跑"：
 * 策略一旦被人改掉（比如改成"有一条不合法就整批拒绝"），测试必须失败。
 */
@ExtendWith(MockitoExtension.class)
class ClientLogServiceTest {

    @Mock
    private ClientLogMapper clientLogMapper;

    private ClientLogService clientLogService;

    @BeforeEach
    void setUp() {
        clientLogService = new ClientLogService(clientLogMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("级别非法或消息为空时逐条跳过，不整批拒绝")
    void shouldSkipInvalidItemsInsteadOfRejectingWholeBatch() {
        ClientLogReportRequest request = request(
                item("info", "正常的一条"),
                item("VERBOSE", "级别不存在"),
                item("warn", "   "),
                item(null, "没有级别"));

        when(clientLogMapper.insertBatch(anyList())).thenReturn(1);

        ClientLogReportResultVO result = clientLogService.report(request, "127.0.0.1");

        // 关键断言：合法的 1 条必须落库，而不是被另外 3 条拖累
        assertThat(result.getAccepted()).isEqualTo(1);
        assertThat(result.getRejected()).isEqualTo(3);

        ArgumentCaptor<List<ClientLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientLogMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("整批条目都不合法时不调用 insertBatch")
    void shouldNotTouchDatabaseWhenNothingIsValid() {
        ClientLogReportRequest request = request(item("nope", "x"));

        ClientLogReportResultVO result = clientLogService.report(request, "127.0.0.1");

        assertThat(result.getAccepted()).isZero();
        assertThat(result.getRejected()).isEqualTo(1);
        // 空集合会让 <foreach> 生成残缺 SQL，所以这里必须一次都不要调用
        verify(clientLogMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("级别大小写不敏感，统一按小写落库")
    void shouldNormalizeLevelToLowerCase() {
        ClientLogReportRequest request = request(item("ERROR", "大写级别"));
        when(clientLogMapper.insertBatch(anyList())).thenReturn(1);

        clientLogService.report(request, "127.0.0.1");

        ArgumentCaptor<List<ClientLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientLogMapper).insertBatch(captor.capture());
        assertThat(captor.getValue().get(0).getLevel()).isEqualTo("error");
    }

    @Test
    @DisplayName("条目级元数据优先，缺省回落到信封级")
    void shouldFallBackToEnvelopeMetadata() {
        ClientLogReportRequest request = request(item("info", "无 sessionId 的条目"));
        request.setSessionId("envelope-session");
        request.setRelease("9.9.9");
        when(clientLogMapper.insertBatch(anyList())).thenReturn(1);

        clientLogService.report(request, "127.0.0.1");

        ArgumentCaptor<List<ClientLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientLogMapper).insertBatch(captor.capture());
        ClientLogEntity saved = captor.getValue().get(0);
        assertThat(saved.getSessionId()).isEqualTo("envelope-session");
        assertThat(saved.getRelease()).isEqualTo("9.9.9");
    }

    @Test
    @DisplayName("超长消息被截断而非整条丢弃，且留下截断标记")
    void shouldTruncateLongMessageInsteadOfDroppingIt() {
        String longMessage = "A".repeat(5000);
        ClientLogReportRequest request = request(item("info", longMessage));
        when(clientLogMapper.insertBatch(anyList())).thenReturn(1);

        clientLogService.report(request, "127.0.0.1");

        ArgumentCaptor<List<ClientLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientLogMapper).insertBatch(captor.capture());
        String saved = captor.getValue().get(0).getMessage();
        assertThat(saved).startsWith("AAAA");
        assertThat(saved).endsWith("…(已截断)");
    }

    @Test
    @DisplayName("data / context 以 JSON 落库，查询时可还原为结构")
    void shouldSerializeStructuredFields() {
        ClientLogItem item = item("info", "带数据");
        item.setData(List.of(1, "two"));
        item.setContext(Map.of("userId", "u-1"));
        ClientLogReportRequest request = request(item);
        when(clientLogMapper.insertBatch(anyList())).thenReturn(1);

        clientLogService.report(request, "127.0.0.1");

        ArgumentCaptor<List<ClientLogEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientLogMapper).insertBatch(captor.capture());
        ClientLogEntity saved = captor.getValue().get(0);
        assertThat(saved.getDataJson()).isEqualTo("[1,\"two\"]");
        assertThat(saved.getContextJson()).isEqualTo("{\"userId\":\"u-1\"}");
    }

    private static ClientLogReportRequest request(ClientLogItem... items) {
        ClientLogReportRequest request = new ClientLogReportRequest();
        request.setApp("unit-test");
        request.setLogs(List.of(items));
        return request;
    }

    private static ClientLogItem item(String level, String message) {
        ClientLogItem item = new ClientLogItem();
        item.setLevel(level);
        item.setMessage(message);
        item.setNamespace("unit › test");
        item.setTimestamp(1759999999999L);
        return item;
    }

}
