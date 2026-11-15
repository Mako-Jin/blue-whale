package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.api.FileShareApi;
import com.bluewhale.file.model.command.CreateShareCommand;
import com.bluewhale.file.model.query.FileSharePageQuery;
import com.bluewhale.file.model.request.FileShareCreateRequest;
import com.bluewhale.file.model.request.FileSharePageRequest;
import com.bluewhale.file.model.response.FileShareAccessResponse;
import com.bluewhale.file.model.response.FileShareCreateResponse;
import com.bluewhale.file.model.response.FileShareResponse;
import com.bluewhale.file.model.result.FileShareAccessResult;
import com.bluewhale.file.model.result.FileShareResult;
import com.bluewhale.file.service.FileShareService;
import com.bluewhale.file.support.FileContentResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/**
 * 文件分享接口实现（Web 适配层）：协议适配 + Request ↔ Command/Query 转换。
 *
 * <p>创建、列表与撤销受 {@code sys:file:*} 权限码头保护；访问与下载不标注权限注解，
 * 任意登录用户凭令牌即可访问。密码从 {@code X-Share-Password} 头读取，不进入 URL。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class FileShareController implements FileShareApi {

    /** 文件管理权限码：持有者可管理全租户分享 */
    private static final String PERM_FILE_MANAGE = "sys:file:manage";

    private final FileShareService fileShareService;

    @Override
    @RequirePermission("sys:file:upload")
    public Result<FileShareCreateResponse> create(FileShareCreateRequest request, HttpServletRequest httpRequest) {
        CreateShareCommand command = new CreateShareCommand(request.getFileId(), request.getExpireHours(),
                request.getMaxDownloads(), request.getPassword(),
                UserContext.requireUserId(httpRequest), canManage(httpRequest));
        return Result.ok(FileShareCreateResponse.from(fileShareService.create(command)));
    }

    @Override
    @RequirePermission("sys:file:list")
    public Result<PageResult<FileShareResponse>> page(FileSharePageRequest request,
                                                      HttpServletRequest httpRequest) {
        FileSharePageQuery query = new FileSharePageQuery(UserContext.requireUserId(httpRequest),
                canManage(httpRequest), request.getCurrent(), request.getSize());
        PageResult<FileShareResult> page = fileShareService.page(query);
        return Result.ok(PageResult.of(page.getRecords().stream().map(FileShareResponse::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<Void> revoke(Long id, HttpServletRequest httpRequest) {
        fileShareService.revoke(id, UserContext.requireUserId(httpRequest), canManage(httpRequest));
        return Result.ok();
    }

    @Override
    public Result<FileShareAccessResponse> access(String token, String password) {
        return Result.ok(FileShareAccessResponse.from(fileShareService.access(token, password)));
    }

    @Override
    public ResponseEntity<Resource> download(String token, String password, String range, String ifRange) {
        FileShareAccessResult access = fileShareService.access(token, password);
        if (access.needPassword()) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "需要分享密码");
        }
        return FileContentResponses.build(access.file(), false, range, ifRange,
                new FileContentResponses.ContentSource() {
                    @Override
                    public InputStream openAll() {
                        return fileShareService.openContent(token, password, null, null);
                    }

                    @Override
                    public InputStream openRange(long offset, long length) {
                        return fileShareService.openContent(token, password, offset, length);
                    }
                });
    }

    /**
     * 判定当前请求是否持有文件管理权限（{@code sys:file:manage}，超管天然具备）
     *
     * @param request 当前请求
     * @return 持有管理权限时返回 {@code true}
     */
    private static boolean canManage(HttpServletRequest request) {
        return UserContext.hasPermission(request, PERM_FILE_MANAGE);
    }
}
