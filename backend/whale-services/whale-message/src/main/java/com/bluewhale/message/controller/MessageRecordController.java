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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息记录查询接口。
 *
 * <p>服务内路径 {@code /message/record/**}；经网关按 {@code Path=/message/**} 转发，
 * 受 {@code sys:message:record:list} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/message/record")
@RequiredArgsConstructor
public class MessageRecordController {

    private final MessageRecordService messageRecordService;

    private final MessageSendService messageSendService;

    /**
     * 分页查询消息记录。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:message:record:list")
    public Result<PageResult<MessageRecordVO>> page(MessageRecordQueryRequest request) {
        return Result.ok(messageRecordService.page(request));
    }

    /**
     * 查询消息详情（含投递明细）。
     *
     * @param id 消息ID
     * @return 消息详情
     */
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
    @PostMapping("/{id}/retry")
    @RequirePermission("sys:message:record:retry")
    @AuditLog(operation = "重试消息投递", type = OperateType.UPDATE,
            targetType = "msg_record", targetId = "#id")
    public Result<SendMessageResult> retry(@PathVariable Long id) {
        return Result.ok(messageSendService.retry(id));
    }
}
