package com.bluewhale.pdm.change.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.change.dto.PdmChgApprovalCallbackRequest;
import com.bluewhale.pdm.change.service.PdmChgApprovalCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDM 变更审批内网回调接口
 *
 * <p>仅供 OA / whale-workflow 内网调用；网关已把 {@code /pdm/internal/**} 对外拒绝，
 * 因此不挂功能权限码。重复回调由服务层按审批实例号幂等处理。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping("/pdm/internal/change")
@RequiredArgsConstructor
public class PdmChgInternalCallbackController {

    private final PdmChgApprovalCallbackService callbackService;

    /**
     * 审批状态回调。
     *
     * @param request 回调请求
     * @return 空响应体
     */
    @PostMapping("/approval-callback")
    public Result<Void> approvalCallback(@Valid @RequestBody PdmChgApprovalCallbackRequest request) {
        callbackService.handle(request);
        return Result.ok();
    }
}
