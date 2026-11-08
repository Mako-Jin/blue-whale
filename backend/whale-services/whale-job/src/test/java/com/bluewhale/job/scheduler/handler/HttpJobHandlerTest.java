package com.bluewhale.job.scheduler.handler;

import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.scheduler.JobExecutionContext;
import com.bluewhale.job.service.JobExecutorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link HttpJobHandler} 派发级故障转移测试。
 *
 * <p>覆盖三条口径：</p>
 * <ol>
 *   <li>连接被拒 → 换下一个候选并把死地址临时摘除（容器重建后 IP 重排的兜底）；</li>
 *   <li>业务失败 / 读超时 → <b>不</b>改派（请求可能已执行，改派会重复副作用）；</li>
 *   <li>全部候选不可达 → 抛出最后一次的不可达原因，便于排障。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HTTP 执行器派发故障转移")
class HttpJobHandlerTest {

    /** 执行器注册表服务 */
    @Mock
    private JobExecutorService jobExecutorService;

    /** RestClient 构造器提供者（本测试不走真实 HTTP） */
    @Mock
    private ObjectProvider<org.springframework.web.client.RestClient.Builder> restClientBuilderProvider;

    /**
     * 首选不可达时改派到下一候选，并把死地址摘除。
     */
    @Test
    @DisplayName("连接被拒：改派下一候选并摘除死地址")
    void shouldFailoverWhenConnectRefused() {
        HttpJobHandler handler = newHandler();
        JobExecutionContext context = context();
        HttpJobHandler.DispatchTarget dead = new HttpJobHandler.DispatchTarget("172.18.0.9:9007", "http://dead");
        HttpJobHandler.DispatchTarget alive = new HttpJobHandler.DispatchTarget("172.18.0.12:9007", "http://alive");
        AtomicReference<String> hit = new AtomicReference<>();

        String result = handler.dispatch(context, List.of(dead, alive), target -> {
            if (target == dead) {
                throw unreachable();
            }
            hit.set(target.address());
            return "OK: done";
        });

        assertEquals("OK: done", result);
        assertEquals("172.18.0.12:9007", hit.get());
        assertEquals("172.18.0.12:9007", context.getResolvedAddress());
        verify(jobExecutorService).markUnreachable(1L, "whale-crm", "172.18.0.9:9007");
    }

    /**
     * 业务失败不得改派：对端已执行，换实例会重复副作用。
     */
    @Test
    @DisplayName("业务失败不改派")
    void shouldNotFailoverOnBusinessFailure() {
        HttpJobHandler handler = newHandler();
        JobExecutionContext context = context();
        HttpJobHandler.DispatchTarget first = new HttpJobHandler.DispatchTarget("172.18.0.12:9007", "http://a");
        HttpJobHandler.DispatchTarget second = new HttpJobHandler.DispatchTarget("172.18.0.13:9007", "http://b");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> handler.dispatch(context, List.of(first, second), target -> {
                    throw new IllegalStateException("业务方返回失败: code=500");
                }));

        assertEquals("业务方返回失败: code=500", e.getMessage());
        assertEquals("172.18.0.12:9007", context.getResolvedAddress());
        verify(jobExecutorService, never()).markUnreachable(anyLong(), anyString(), anyString());
    }

    /**
     * 全部候选不可达时抛出不可达原因，而不是静默返回。
     */
    @Test
    @DisplayName("全部候选不可达：抛出不可达原因")
    void shouldThrowWhenAllCandidatesUnreachable() {
        HttpJobHandler handler = newHandler();
        JobExecutionContext context = context();
        List<HttpJobHandler.DispatchTarget> targets = List.of(
                new HttpJobHandler.DispatchTarget("172.18.0.9:9007", "http://a"),
                new HttpJobHandler.DispatchTarget("172.18.0.10:9007", "http://b"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> handler.dispatch(context, targets, target -> {
                    throw unreachable();
                }));

        assertTrue(e.getMessage().contains("执行器不可达"));
        verify(jobExecutorService).markUnreachable(1L, "whale-crm", "172.18.0.9:9007");
        verify(jobExecutorService).markUnreachable(1L, "whale-crm", "172.18.0.10:9007");
    }

    /**
     * 连接被拒算连接层失败；读超时不属于，改派会导致重复执行。
     */
    @Test
    @DisplayName("连接失败判定：拒连为真、超时为假")
    void shouldDistinguishConnectFailureFromTimeout() {
        HttpJobHandler handler = newHandler();

        assertTrue(handler.isConnectFailure(
                new ResourceAccessException("I/O error", new ConnectException("Connection refused"))));
        assertFalse(handler.isConnectFailure(
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"))));
        assertFalse(handler.isConnectFailure(new ResourceAccessException("I/O error")));
    }

    /**
     * 构造被测执行器。
     *
     * @return 执行器实例
     */
    private HttpJobHandler newHandler() {
        return new HttpJobHandler(jobExecutorService, new JobProperties(), null, restClientBuilderProvider);
    }

    /**
     * 构造租户 1 下 whale-crm 的执行上下文。
     *
     * @return 执行上下文
     */
    private JobExecutionContext context() {
        return new JobExecutionContext(1L, "订单推送", "{}", 0, 1, 0, 1L, "whale-crm", "crm.order.push");
    }

    /**
     * 模拟连接被拒。
     *
     * @return 不可达异常
     */
    private HttpJobHandler.ExecutorUnreachableException unreachable() {
        return new HttpJobHandler.ExecutorUnreachableException("执行器不可达: Connection refused",
                new ConnectException("Connection refused"));
    }

    /**
     * 候选为空时直接给出不可派发原因，避免退化成空指针。
     */
    @Test
    @DisplayName("空候选列表：抛出不可派发")
    void shouldRejectEmptyTargets() {
        HttpJobHandler handler = newHandler();

        assertThrows(JobNotDispatchableException.class,
                () -> handler.dispatch(context(), List.of(), target -> "unused"));
        verify(jobExecutorService, never()).markUnreachable(anyLong(), anyString(), anyString());
    }
}
