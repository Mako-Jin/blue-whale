package com.bluewhale.message.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.message.dto.MessageTemplateQueryRequest;
import com.bluewhale.message.dto.MessageTemplateSaveRequest;
import com.bluewhale.message.service.MessageTemplateService;
import com.bluewhale.message.vo.MessageTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息模板管理接口。
 *
 * <p>服务内路径 {@code /message/template/**}，经网关按 {@code Path=/message/**} 转发；
 * 全部接口受 {@code sys:message:template:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/message/template")
@RequiredArgsConstructor
public class MessageTemplateController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "msg_template";

    private final MessageTemplateService messageTemplateService;

    /**
     * 分页查询模板。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:message:template:list")
    public Result<PageResult<MessageTemplateVO>> page(MessageTemplateQueryRequest request) {
        return Result.ok(messageTemplateService.page(request));
    }

    /**
     * 模板详情。
     *
     * @param id 模板ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:message:template:list")
    public Result<MessageTemplateVO> detail(@PathVariable Long id) {
        return Result.ok(messageTemplateService.detail(id));
    }

    /**
     * 新增模板。
     *
     * @param request 新增请求
     * @return 新增后的模板
     */
    @PostMapping
    @RequirePermission("sys:message:template:add")
    @AuditLog(operation = "新增消息模板", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MessageTemplateVO> create(@Valid @RequestBody MessageTemplateSaveRequest request) {
        return Result.ok(messageTemplateService.create(request));
    }

    /**
     * 更新模板。
     *
     * @param id      模板ID
     * @param request 更新请求
     * @return 更新后的模板
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:message:template:edit")
    @AuditLog(operation = "更新消息模板", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MessageTemplateVO> update(@PathVariable Long id,
                                            @Valid @RequestBody MessageTemplateSaveRequest request) {
        return Result.ok(messageTemplateService.update(id, request));
    }

    /**
     * 删除模板（逻辑删除）。
     *
     * @param id 模板ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:message:template:delete")
    @AuditLog(operation = "删除消息模板", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        messageTemplateService.delete(id);
        return Result.ok();
    }
}
