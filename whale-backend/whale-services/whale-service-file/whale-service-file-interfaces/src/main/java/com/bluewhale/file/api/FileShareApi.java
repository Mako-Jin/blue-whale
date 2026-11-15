package com.bluewhale.file.api;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.request.FileShareCreateRequest;
import com.bluewhale.file.model.request.FileSharePageRequest;
import com.bluewhale.file.model.response.FileShareAccessResponse;
import com.bluewhale.file.model.response.FileShareCreateResponse;
import com.bluewhale.file.model.response.FileShareResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 文件分享链接 API 契约。
 *
 * <p>创建、列表与撤销需登录并持有对应文件权限；访问与下载接口不标注权限注解，
 * 任意登录用户凭令牌即可访问（匿名开放需网关白名单，暂不支持）。
 * 密码通过请求头传递，避免进入 URL、访问日志与 Referer。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/share", version = "1")
public interface FileShareApi {

    /** 分享密码请求头 */
    String SHARE_PASSWORD_HEADER = "X-Share-Password";

    /**
     * 创建分享链接。
     *
     * @param request 创建请求：fileId、expireHours、maxDownloads、password
     * @param httpRequest 当前请求（用于读取调用者与权限码）
     * @return 创建结果（令牌与失效时间）
     */
    @PostMapping
    Result<FileShareCreateResponse> create(@Valid @RequestBody FileShareCreateRequest request,
                                           HttpServletRequest httpRequest);

    /**
     * 分页查询分享链接。
     *
     * @param request 分页参数
     * @param httpRequest 当前请求（用于判定创建人可见范围）
     * @return 分享分页结果
     */
    @GetMapping("/page")
    Result<PageResult<FileShareResponse>> page(FileSharePageRequest request, HttpServletRequest httpRequest);

    /**
     * 撤销分享链接。
     *
     * @param id 分享ID
     * @param httpRequest 当前请求（用于判定创建人或管理权限）
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    Result<Void> revoke(@PathVariable Long id, HttpServletRequest httpRequest);

    /**
     * 凭令牌查看分享元数据。
     *
     * @param token 分享令牌
     * @param password 分享密码，可为 {@code null}
     * @return 分享元数据；需要密码且未提供时仅返回 {@code needPassword=true}
     */
    @GetMapping("/{token}")
    Result<FileShareAccessResponse> access(@PathVariable String token,
                                           @RequestHeader(value = SHARE_PASSWORD_HEADER, required = false)
                                           String password);

    /**
     * 凭令牌下载文件内容（支持 HTTP Range 分段，每次请求占用一次下载次数）。
     *
     * @param token 分享令牌
     * @param password 分享密码，可为 {@code null}
     * @param range 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRange 请求的 {@code If-Range} 头，可为 {@code null}
     * @return 文件内容流式响应
     */
    @GetMapping("/{token}/download")
    ResponseEntity<Resource> download(@PathVariable String token,
                                      @RequestHeader(value = SHARE_PASSWORD_HEADER, required = false)
                                      String password,
                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                      @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange);
}
