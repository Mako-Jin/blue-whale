package com.bluewhale.message.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.message.dto.PreferenceResolveRequest;
import com.bluewhale.message.service.MessagePreferenceService;
import com.bluewhale.message.vo.PreferenceDecisionVO;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息偏好内网接口：按用户偏好裁决通知渠道（Wave 6.17b）。
 *
 * <p>服务内路径前缀 {@code /message/v1/internal}，供 twin 等业务服务经内网直连调用；
 * 网关 internal-deny 统一 403，外部不可达，故<b>刻意不加</b>权限码注解。
 * 解析失败一律 fail-open（按调用方默认放行），绝不阻塞调用方的外发链路。</p>
 *
 * @author BlueWhale
 */
@Hidden
@RestController
@RequestMapping("/message/v1/internal")
@RequiredArgsConstructor
public class MessagePreferenceInternalController {

    /** 通知偏好服务 */
    private final MessagePreferenceService messagePreferenceService;

    /**
     * 按用户偏好裁决通知渠道。
     *
     * @param request 解析请求
     * @return 每个用户一个决策项
     */
    @PostMapping("/preferences/resolve")
    public Result<List<PreferenceDecisionVO>> resolve(
            @Valid @RequestBody PreferenceResolveRequest request) {
        return Result.ok(messagePreferenceService.resolve(request));
    }
}
