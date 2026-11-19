package com.bluewhale.message.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.message.dto.MessageRecordQueryRequest;
import com.bluewhale.message.service.MessageRecordService;
import com.bluewhale.message.service.MessageSendService;
import com.bluewhale.message.vo.MessageRecordVO;
import com.bluewhale.message.vo.SendMessageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息记录查询接口。
 *
 * <p>服务内路径 {@code /message/v1/record/**}；经网关按 {@code Path=/message/**} 转发，
 * 受 {@code sys:message:record:list} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/message/v1/record")
@RequiredArgsConstructor
@Tag(name = "消息记录", description = "消息记录查询与失败重试（权限：sys:message:record:list / sys:message:record:retry）")
public class MessageRecordController {

    private final MessageRecordService messageRecordService;

    private final MessageSendService messageSendService;

    /**
     * 分页查询消息记录。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询消息记录", description = "按状态/渠道/业务类型等条件查询，接收人已脱敏")
    @GetMapping("/page")
    @RequirePermission("sys:message:record:list")
    public Result<PageResult<MessageRecordVO>> page(@Valid MessageRecordQueryRequest request) {
        return Result.ok(messageRecordService.page(request));
    }

    /**
     * 查询消息详情（含投递明细）。
     *
     * @param id 消息ID
     * @return 消息详情
     */
    @Operation(summary = "查询消息详情", description = "含各渠道投递明细与供应商标识")
    @GetMapping("/{id}")
    @RequirePermission("sys:message:record:list")
    public Result<MessageRecordVO> detail(@PathVariable Long id) {
        return Result.ok(messageRecordService.detail(id));
    }

    /**
     * 重试消息中失败的投递。
     *
     * @param id 消息ID
     * @return 重试后的消息状态与投递明细
     */
    @Operation(summary = "重试失败投递", description = "仅重试 FAILED 投递；已过期消息不再投递，记 EXPIRED")
    @PostMapping("/{id}/retry")
    @RequirePermission("sys:message:record:retry")
    @AuditLog(operation = "重试消息投递", type = OperateType.UPDATE,
            targetType = "msg_record", targetId = "#id")
    public Result<SendMessageResult> retry(@PathVariable Long id) {
        return Result.ok(messageSendService.retry(id));
    }
}
