package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.ConfigBatchGetRequest;
import com.bluewhale.auth.interfaces.dto.request.ConfigSetRequest;
import com.bluewhale.auth.interfaces.dto.response.ConfigBatchItemResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 用户配置内网 API 契约：供 whale-message 等服务批量读取用户/租户配置的合并结果。
 *
 * <p>路径以 {@code /user/v1/internal} 开头，网关收口外部访问，内网调用须携带
 * {@code X-Internal-Token} 与 {@code X-Tenant-Id}。本契约刻意不声明业务权限点。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/user/v{version}/internal/config", version = "1")
public interface UserConfigInternalApi {

    /**
     * 批量读取多个用户在多个配置键上的生效值（合并优先级：用户 &gt; 租户 &gt; 未配置）
     *
     * @param body 批量读取请求（tenantId + userIds + keys）
     * @return 每个 (userId, configKey) 组合一个结果项
     */
    @PostMapping("/batch-get")
    Result<List<ConfigBatchItemResponse>> batchGet(@Valid @RequestBody ConfigBatchGetRequest body);

    /**
     * 写入（upsert）一个用户在某个配置键上的值
     *
     * <p>{@code tenantId} 必须与请求头 {@code X-Tenant-Id} 一致，防止调用方串租户写库。</p>
     *
     * @param body 写入请求
     * @return 成功
     */
    @PostMapping("/set")
    Result<Void> set(@Valid @RequestBody ConfigSetRequest body);
}
