package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.api.FileBatchApi;
import com.bluewhale.file.model.command.FileBatchDeleteCommand;
import com.bluewhale.file.model.request.FileBatchDeleteRequest;
import com.bluewhale.file.model.response.FileBatchDeleteResponse;
import com.bluewhale.file.model.result.FileArchiveEntryResult;
import com.bluewhale.file.model.result.FileBatchDeleteResult;
import com.bluewhale.file.service.FileArchiveService;
import com.bluewhale.file.service.FileInfoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 文件批量操作接口实现（Web 适配层）：批量删除与打包下载。
 *
 * <p>打包下载先由应用层完成全部校验再返回流式响应，避免响应已提交后才暴露越权或参数错误；
 * 响应不声明 {@code Content-Length}，由容器分块写出。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class FileBatchController implements FileBatchApi {

    /** 文件管理权限码：持有者可操作全租户文件 */
    private static final String PERM_FILE_MANAGE = "sys:file:manage";

    /** ZIP 响应媒体类型 */
    private static final String ZIP_CONTENT_TYPE = "application/zip";

    /** 禁止浏览器嗅探响应内容类型的安全响应头 */
    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";

    /** 禁止嗅探的响应头取值 */
    private static final String NOSNIFF_VALUE = "nosniff";

    /** 打包文件名前缀 */
    private static final String ARCHIVE_NAME_PREFIX = "files-";

    /** 打包文件名后缀 */
    private static final String ARCHIVE_NAME_SUFFIX = ".zip";

    /** 打包文件名时间戳格式 */
    private static final DateTimeFormatter ARCHIVE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final FileInfoService fileInfoService;

    private final FileArchiveService fileArchiveService;

    @Override
    @RequirePermission("sys:file:delete")
    public Result<FileBatchDeleteResponse> batchDelete(FileBatchDeleteRequest request,
                                                       HttpServletRequest httpRequest) {
        FileBatchDeleteResult result = fileInfoService.batchDelete(new FileBatchDeleteCommand(request.getIds(),
                UserContext.requireUserId(httpRequest), canManage(httpRequest)));
        return Result.ok(FileBatchDeleteResponse.from(result));
    }

    @Override
    @RequirePermission("sys:file:download")
    public ResponseEntity<StreamingResponseBody> batchDownload(List<Long> ids, HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        boolean canManage = canManage(httpRequest);
        List<FileArchiveEntryResult> entries = fileArchiveService.prepare(ids, requesterId, canManage);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(ZIP_CONTENT_TYPE));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(ARCHIVE_NAME_PREFIX + LocalDateTime.now().format(ARCHIVE_NAME_FORMATTER)
                        + ARCHIVE_NAME_SUFFIX, StandardCharsets.UTF_8).build().toString());
        headers.set(NOSNIFF_HEADER, NOSNIFF_VALUE);
        StreamingResponseBody body = outputStream ->
                fileArchiveService.writeZip(entries, requesterId, canManage, outputStream);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
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
