package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.api.FileChunkUploadApi;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.ChunkUploadCompleteCommand;
import com.bluewhale.file.model.command.ChunkUploadInitCommand;
import com.bluewhale.file.model.request.ChunkUploadCompleteRequest;
import com.bluewhale.file.model.request.ChunkUploadInitRequest;
import com.bluewhale.file.model.response.ChunkUploadInitResponse;
import com.bluewhale.file.model.response.FileUploadResponse;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件分片上传接口实现（G1 大文件上传主通道）。
 *
 * <p>服务内路径前缀 {@code /file/v1/upload/chunk}，按网关可信注入的
 * {@code X-Permission-Codes} 解析生效上限与配额（视频 2GB / 8GB、电影 4GB / 不限），
 * 未命中规则回落全局默认 50MB / 1GB。complete 阶段取「init 登记上限」与「当前请求上限」
 * 中更严格者，权限在传输过程中被回收时不会放大上限。</p>
 *
 * <p>单片接口在接口层按生效分片大小（{@code whale.file.chunk-size-bytes}）预检，
 * 超限单片直接拒绝，避免超大单片全量读入堆内存。</p>
 *
 * @author BlueWhale
 */
@RestController
@Validated
@RequiredArgsConstructor
public class FileChunkUploadController implements FileChunkUploadApi {

    private final FileInfoService fileInfoService;

    private final FileUploadPolicy fileUploadPolicy;

    private final FileAssembler fileAssembler;

    @Override
    @RequirePermission("sys:file:upload")
    public Result<ChunkUploadInitResponse> initChunkUpload(@Valid @RequestBody ChunkUploadInitRequest request,
                                                           HttpServletRequest httpRequest) {
        ChunkUploadInitCommand command = new ChunkUploadInitCommand(request.getFileName(),
                request.getContentType(), request.getSize(), request.getBucket(), request.getUploadId(),
                uploadLimits(httpRequest));
        return Result.ok(fileAssembler.toChunkInitResponse(fileInfoService.initChunkUpload(command)));
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<List<Integer>> uploadChunkPart(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadId")
            @NotBlank(message = "{validation.file.chunk.upload-id-required}") String uploadId,
            @RequestParam("index")
            @Min(value = 0, message = "{validation.file.chunk.index-min}") int index) {
        return Result.ok(fileInfoService.uploadChunkPart(uploadId, index,
                toUploadContent(file, fileInfoService.chunkSizeBytes())));
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<List<Integer>> chunkUploadStatus(@PathVariable String uploadId) {
        return Result.ok(fileInfoService.chunkUploadStatus(uploadId));
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<FileUploadResponse> completeChunkUpload(@Valid @RequestBody ChunkUploadCompleteRequest request,
                                                          HttpServletRequest httpRequest) {
        ChunkUploadCompleteCommand command = new ChunkUploadCompleteCommand(request.getUploadId(),
                request.getFileName(), request.getSize(), request.getBucket(),
                UserContext.requireUserId(httpRequest), uploadLimits(httpRequest));
        return Result.ok(fileAssembler.toUploadResponse(fileInfoService.completeChunkUpload(command)));
    }

    /**
     * 解析当前请求生效的上传上限与配额（G1 大文件上传策略）
     *
     * @param request 当前请求
     * @return 生效上传上限；无权限码时返回全局默认值
     */
    private UploadLimits uploadLimits(HttpServletRequest request) {
        return fileUploadPolicy.resolve(UserContext.permissionCodes(request));
    }

    /**
     * 将 Web 上传对象适配为应用层中立上传内容，并按声明大小预检防全量读入 OOM
     *
     * @param file 上传的文件
     * @param maxBytes 允许全量读入的最大字节数
     * @return 上传内容
     * @throws BizException 超出单片上限或读取内容失败时抛出
     */
    private static UploadContent toUploadContent(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.PARAM_ERROR, "file.common.chunk-size-exceeded", "分片大小超出上限");
        }
        try {
            return new UploadContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
