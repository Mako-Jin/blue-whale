package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.userconfig.UserConfigAppService;
import com.bluewhale.auth.application.userconfig.UserConfigBatchService;
import com.bluewhale.auth.application.userconfig.command.SaveUserConfigCommand;
import com.bluewhale.auth.application.userconfig.vo.ConfigBatchItemVO;
import com.bluewhale.auth.interfaces.dto.ConfigBatchGetRequest;
import com.bluewhale.auth.interfaces.dto.ConfigSetRequest;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户配置内网接口：供 whale-message 等服务批量读取用户/租户配置的合并结果。
 *
 * <p>路径固定以 {@code /user/internal} 开头，已列入网关
 * {@code whale.gateway.internal-deny.patterns}（外部经网关访问一律 403），且命中
 * auth {@code SecurityConfig.permitAll} 的 {@code /user/internal/**} 白名单；
 * 经内网直连时须携带 {@code X-Internal-Token} 与 {@code X-Tenant-Id}。
 * 本接口<b>刻意不加</b> {@code @RequirePermission}——服务间调用没有用户权限码，
 * 标注必然 403。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/user/internal/config")
@RequiredArgsConstructor
public class UserConfigInternalController {

    /** 批量合并读取服务 */
    private final UserConfigBatchService userConfigBatchService;

    /** 用户配置单条写服务（复用自助写入的同一实现） */
    private final UserConfigAppService userConfigAppService;

    /**
     * 批量读取多个用户在多个配置键上的生效值（合并优先级：用户 &gt; 租户 &gt; 未配置）
     *
     * @param body 批量读取请求（tenantId + userIds + keys）
     * @return 每个 (userId, configKey) 组合一个结果项
     */
    @PostMapping("/batch-get")
    public Result<List<ConfigBatchItemVO>> batchGet(@jakarta.validation.Valid
                                                    @RequestBody ConfigBatchGetRequest body) {
        return Result.ok(userConfigBatchService.batchGet(
                body.tenantId(), body.userIds(), body.keys()));
    }

    /**
     * 写入（upsert）一个用户在某个配置键上的值。
     *
     * <p>供业务服务把「用户偏好」落到 auth 统一配置表（Wave 6.25 起 whale-scene 使用），
     * auth 只做存储安全校验（键口径 / JSON 合法性 / 大小上限），业务语义由调用方解释。
     * {@code tenantId} 必须与请求头 {@code X-Tenant-Id} 一致，防止调用方串租户写库。</p>
     *
     * @param body 写入请求
     * @return 成功
     */
    @PostMapping("/set")
    public Result<Void> set(@jakarta.validation.Valid @RequestBody ConfigSetRequest body) {
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
