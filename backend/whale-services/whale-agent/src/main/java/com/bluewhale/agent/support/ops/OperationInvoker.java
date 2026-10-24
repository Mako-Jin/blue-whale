package com.bluewhale.agent.support.ops;

import java.io.IOException;
import java.util.Map;

/**
 * 平台写操作执行器：把已确认的请求转发到目标业务服务。
 *
 * <p>抽象为接口便于单测注入桩实现，默认实现为 {@link HttpOperationInvoker}。</p>
 *
 * @author BlueWhale
 */
public interface OperationInvoker {

    /**
     * 执行写操作。
     *
     * @param url             目标地址（完整 URL）
     * @param body            请求体
     * @param userId          发起用户ID（透传 {@code X-User-Id}）
     * @param permissionCodes 用户权限码（透传 {@code X-Permission-Codes}）
     * @return 下游 {@code Result.data} 内容
     * @throws IOException          HTTP 非 2xx、业务码非 0 或网络失败
     * @throws InterruptedException 线程中断
     */
    Map<String, Object> invoke(String url, Map<String, Object> body, Long userId, String permissionCodes)
            throws IOException, InterruptedException;
}
