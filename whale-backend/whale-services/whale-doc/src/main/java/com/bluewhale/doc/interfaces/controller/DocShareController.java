package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocShareService;
import com.bluewhale.doc.interfaces.dto.DocPageShareCreateRequest;
import com.bluewhale.doc.interfaces.vo.DocPageShareVO;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 页面登录内分享接口（M3）。
 *
 * <p>{@code /doc/share/{token}} 仅要求登录，不要求空间成员身份；
 * 其余管理接口要求空间 EDITOR 及以上。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocShareController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_page_share";

    private final DocShareService shareService;

    /**
     * 查询页面分享记录。
     *
     * @param id          页面ID
     * @param httpRequest 当前请求
     * @return 分享记录
     */
    @GetMapping("/page/{id}/share")
    @RequirePermission("sys:doc:share:manage")
    public Result<List<DocPageShareVO>> list(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(shareService.list(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 创建页面分享链接。
     *
     * @param id          页面ID
     * @param request     创建请求
     * @param httpRequest 当前请求
     * @return 分享记录
     */
    @PostMapping("/page/{id}/share")
    @RequirePermission("sys:doc:share:manage")
    @AuditLog(operation = "创建文档分享链接", type = OperateType.CREATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<DocPageShareVO> create(@PathVariable Long id,
                                         @Valid @RequestBody DocPageShareCreateRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.ok(shareService.create(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 撤销页面分享链接。
     *
     * @param id          页面ID
     * @param shareId     分享记录ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/page/{id}/share/{shareId}")
    @RequirePermission("sys:doc:share:manage")
    @AuditLog(operation = "撤销文档分享链接", type = OperateType.DELETE, targetType = TARGET_TYPE,
            targetId = "#shareId")
    public Result<Void> revoke(@PathVariable Long id, @PathVariable Long shareId,
                               HttpServletRequest httpRequest) {
        shareService.revoke(id, shareId, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 按令牌读取分享页面（登录内只读）。
     *
     * @param token       分享令牌
     * @param httpRequest 当前请求
     * @return 页面只读视图
     */
    @GetMapping("/share/{token}")
    public Result<DocPageVO> resolve(@PathVariable String token, HttpServletRequest httpRequest) {
        return Result.ok(shareService.resolve(token, UserContext.requireUserId(httpRequest)));
    }
}
