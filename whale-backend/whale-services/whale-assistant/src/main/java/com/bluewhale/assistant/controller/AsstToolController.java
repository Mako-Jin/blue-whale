package com.bluewhale.assistant.controller;

import com.bluewhale.assistant.dto.ToolInfoVO;
import com.bluewhale.assistant.support.ToolRegistryClient;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 工具注册表接口（管理视角，供工具授权页展示可授权工具全集）。
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping(path = "/assistant/v{version}/tools", version = "1")
@RequiredArgsConstructor
public class AsstToolController {

    private final ToolRegistryClient toolRegistryClient;

    /**
     * whale-ai 工具注册表清单。
     *
     * @return 工具描述列表（name/description/riskLevel/permission）
     */
    @GetMapping
    @RequirePermission("sys:assistant:define")
    public Result<List<ToolInfoVO>> list() {
        try {
            List<ToolInfoVO> tools = toolRegistryClient.listToolDescriptions().stream()
                    .map(item -> ToolInfoVO.builder()
                            .name(String.valueOf(item.get("name")))
                            .description(String.valueOf(item.get("description")))
                            .riskLevel(String.valueOf(item.get("riskLevel")))
                            .permission(String.valueOf(item.get("permission")))
                            .build())
                    .toList();
            return Result.ok(tools);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SYSTEM_ERROR, "assistant.tool.registry-interrupted",
                    "工具注册表查询被中断，请稍后再试");
        } catch (IOException e) {
            log.warn("工具注册表查询失败：{}", e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "assistant.tool.registry-unavailable",
                    "工具注册表暂不可用（whale-ai 未就绪），请稍后再试");
        }
    }
}
