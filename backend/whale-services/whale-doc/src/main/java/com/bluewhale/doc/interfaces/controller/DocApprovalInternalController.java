package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocApprovalService;
import com.bluewhale.doc.interfaces.dto.DocApprovalCallbackRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布审批内网回调接口（M3）。
 *
 * <p>仅允许内网服务调用；网关对 {@code /doc/internal/**} 统一拒绝外部访问。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc/internal/approval")
@RequiredArgsConstructor
public class DocApprovalInternalController {

    private final DocApprovalService approvalService;

    /**
     * 接收工作流审批结果。
     *
     * @param request 回调请求
     * @return 是否触发状态变更
     */
    @PostMapping("/callback")
    public Result<Boolean> callback(@Valid @RequestBody DocApprovalCallbackRequest request) {
        return Result.ok(approvalService.handleCallback(request));
    }
}
