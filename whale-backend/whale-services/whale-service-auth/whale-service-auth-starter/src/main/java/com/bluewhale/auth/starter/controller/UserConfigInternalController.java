package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.service.UserConfigAppService;
import com.bluewhale.auth.application.service.UserConfigBatchService;
import com.bluewhale.auth.application.model.command.SaveUserConfigCommand;
import com.bluewhale.auth.interfaces.api.UserConfigInternalApi;
import com.bluewhale.auth.interfaces.dto.request.ConfigBatchGetRequest;
import com.bluewhale.auth.interfaces.dto.request.ConfigSetRequest;
import com.bluewhale.auth.interfaces.dto.response.ConfigBatchItemResponse;
import com.bluewhale.auth.starter.assemble.ConfigAssembler;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户配置内网接口实现（Web 适配层）：仅供服务间内网直连调用。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class UserConfigInternalController implements UserConfigInternalApi {

    private final UserConfigBatchService userConfigBatchService;

    private final UserConfigAppService userConfigAppService;

    private final ConfigAssembler configAssembler;

    /**
     * 批量读取多个用户在多个配置键上的生效值（合并优先级：用户 &gt; 租户 &gt; 未配置）
     *
     * @param body 批量读取请求（tenantId + userIds + keys）
     * @return 每个 (userId, configKey) 组合一个结果项
     */
    @Override
    public Result<List<ConfigBatchItemResponse>> batchGet(@Valid @RequestBody ConfigBatchGetRequest body) {
        return Result.ok(configAssembler.toBatchItemResponses(
                userConfigBatchService.batchGet(body.tenantId(), body.userIds(), body.keys())));
    }

    /**
     * 写入（upsert）一个用户在某个配置键上的值。
     *
     * <p>{@code tenantId} 必须与请求头 {@code X-Tenant-Id} 一致，防止调用方串租户写库。</p>
     *
     * @param body 写入请求
     * @return 成功
     */
    @Override
    public Result<Void> set(@Valid @RequestBody ConfigSetRequest body) {
        Long contextTenantId = TenantContext.requireId();
        if (!contextTenantId.equals(body.tenantId())) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "tenantId 与 X-Tenant-Id 不一致：" + body.tenantId() + " vs " + contextTenantId);
        }
        Long operatorId = body.operatorId() == null ? body.userId() : body.operatorId();
        userConfigAppService.upsert(body.tenantId(), body.userId(),
                new SaveUserConfigCommand(body.configKey(), body.configValue(), operatorId));
        return Result.ok();
    }
}
