package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.cert.application.command.CertificateIssueCommand;
import com.bluewhale.lms.cert.application.command.CertificateRenewCommand;
import com.bluewhale.lms.cert.application.command.CertificateRevokeCommand;
import com.bluewhale.lms.cert.application.service.CertificateAppService;
import com.bluewhale.lms.cert.application.view.CertificateView;
import com.bluewhale.lms.cert.domain.model.CertificateQuery;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 证书接口（M4-1）。
 *
 * <p>{@code /mine} 与 {@code /page} 的差别不只是数据范围：前者把持证人强制为当前登录人，
 * 学员即使伪造查询参数也拿不到别人的证书。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/certificate", version = "1")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateAppService certificateAppService;

    /**
     * 证书分页（管理端）。
     *
     * @param keyword 关键字
     * @param templateId 模板 ID
     * @param userId 持证人用户 ID
     * @param sourceType 来源类型
     * @param status 展示状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.CERT_LIST)
    public Result<PageResult<CertificateView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "templateId", required = false) Long templateId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CertificateQuery query = new CertificateQuery();
        query.setKeyword(keyword);
        query.setTemplateId(templateId);
        query.setUserId(userId);
        query.setSourceType(sourceType);
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(certificateAppService.page(query, actor(request)));
    }

    /**
     * 我的证书。
     *
     * @param status 展示状态
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/mine")
    @RequirePermission(LmsPermissions.CERT_MINE)
    public Result<PageResult<CertificateView>> mine(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CertificateQuery query = new CertificateQuery();
        query.setStatus(status);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(certificateAppService.mine(query, actor(request)));
    }

    /**
     * 证书详情。
     *
     * @param id 证书 ID
     * @param request 当前请求
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(LmsPermissions.CERT_LIST)
    public Result<CertificateView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(certificateAppService.detail(id, actor(request)));
    }

    /**
     * 发证。
     *
     * @param command 发证命令
     * @param request 当前请求
     * @return 证书 ID
     */
    @PostMapping("/issue")
    @RequirePermission(LmsPermissions.CERT_ISSUE)
    @AuditLog(operation = "发放证书", type = OperateType.CREATE, targetType = "lr_certificate")
    public Result<Long> issue(@Valid @RequestBody CertificateIssueCommand command,
                              HttpServletRequest request) {
        return Result.ok(certificateAppService.issue(command, actor(request)));
    }

    /**
     * 撤销证书。
     *
     * @param id 证书 ID
     * @param command 撤销命令
     * @param request 当前请求
     * @return 空
     */
    @PutMapping("/{id}/revoke")
    @RequirePermission(LmsPermissions.CERT_REVOKE)
    @AuditLog(operation = "撤销证书", type = OperateType.UPDATE, targetType = "lr_certificate")
    public Result<Void> revoke(@PathVariable Long id,
                               @Valid @RequestBody CertificateRevokeCommand command,
                               HttpServletRequest request) {
        certificateAppService.revoke(id, command, actor(request));
        return Result.ok();
    }

    /**
     * 换发证书。
     *
     * @param id 原证书 ID
     * @param command 换发命令
     * @param request 当前请求
     * @return 新证书 ID
     */
    @PostMapping("/{id}/renew")
    @RequirePermission(LmsPermissions.CERT_REVOKE)
    @AuditLog(operation = "换发证书", type = OperateType.CREATE, targetType = "lr_certificate")
    public Result<Long> renew(@PathVariable Long id,
                              @Valid @RequestBody CertificateRenewCommand command,
                              HttpServletRequest request) {
        return Result.ok(certificateAppService.renew(id, command, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private LmsActor actor(HttpServletRequest request) {
        return LmsActorContext.from(request);
    }
}
