package com.bluewhale.message.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.message.dto.AnnouncementPublishRequest;
import com.bluewhale.message.dto.AnnouncementQueryRequest;
import com.bluewhale.message.service.MessageAnnouncementService;
import com.bluewhale.message.vo.AnnouncementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * 系统公告（全员广播）管理接口。
 *
 * <p>服务内路径 {@code /message/v1/announce/**}，经网关按 {@code Path=/message/**} 转发；
 * 全部接口受 {@code sys:message:announce:*} 权限保护，写操作均留审计日志。
 * 公告面向当前租户全体用户，收件箱读取时按需展开，无需枚举接收人。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/message/v1/announce")
@Tag(name = "系统公告", description = "公告发布与撤回（权限：sys:message:announce:list / publish / revoke / delete）")
@RequiredArgsConstructor
public class MessageAnnouncementController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "msg_announcement";

    private final MessageAnnouncementService messageAnnouncementService;

    /**
     * 分页查询公告。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询系统公告")
    @GetMapping("/page")
    @RequirePermission("sys:message:announce:list")
    public Result<PageResult<AnnouncementVO>> page(@Valid AnnouncementQueryRequest request) {
        return Result.ok(messageAnnouncementService.page(request));
    }

    /**
     * 发布一条系统公告。
     *
     * @param request     发布请求
     * @param httpRequest 当前请求（读取网关 {@code X-User-Id} 作为发布人）
     * @return 公告视图
     */
    @Operation(summary = "发布系统公告", description = "按目标范围展开为站内信投递，发布即生效")
    @PostMapping
    @RequirePermission("sys:message:announce:publish")
    @AuditLog(operation = "发布系统公告", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<AnnouncementVO> publish(@Valid @RequestBody AnnouncementPublishRequest request,
                                          HttpServletRequest httpRequest) {
        return Result.ok(messageAnnouncementService.publish(request,
                UserContext.findUserId(httpRequest)));
    }

    /**
     * 撤回公告。
     *
     * @param id 公告ID
     * @return 空响应体
     */
    @Operation(summary = "撤回系统公告", description = "撤回后用户不可再看到该公告，已读记录保留")
    @PutMapping("/{id}/revoke")
    @RequirePermission("sys:message:announce:revoke")
    @AuditLog(operation = "撤回系统公告", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> revoke(@PathVariable Long id) {
        messageAnnouncementService.revoke(id);
        return Result.ok();
    }

    /**
     * 删除公告（逻辑删除）。
     *
     * @param id 公告ID
     * @return 空响应体
     */
    @Operation(summary = "删除系统公告", description = "逻辑删除，仅允许删除未发布或已撤回的公告")
    @DeleteMapping("/{id}")
    @RequirePermission("sys:message:announce:delete")
    @AuditLog(operation = "删除系统公告", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        messageAnnouncementService.delete(id);
        return Result.ok();
    }
}
