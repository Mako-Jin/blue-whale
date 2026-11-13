package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.model.UploadContent;
import com.bluewhale.file.service.model.UploadLimits;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileUploadVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件分片上传接口（G1 大文件上传主通道）。
 *
 * <p>服务内路径前缀 {@code /file/upload/chunk}，按网关可信注入的 {@code X-Permission-Codes}
 * 解析生效上限与配额（视频 2GB / 8GB、电影 4GB / 不限），未命中规则回落全局默认 50MB / 1GB。
 * complete 阶段取「init 登记上限」与「当前请求上限」中更严格者，权限在传输过程中被回收时不会放大上限。</p>
 *
 * <p>单片接口在接口层按生效分片大小（{@code whale.file.chunk-size-bytes}）预检，
 * 超限单片直接拒绝，避免超大单片全量读入堆内存。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/file/upload/chunk")
@RequiredArgsConstructor
public class FileChunkUploadController {

    private final FileInfoService fileInfoService;

    private final FileUploadPolicy fileUploadPolicy;

    /**
     * 初始化分片上传会话（支持传入既有 uploadId 续传）。
     *
     * <p>按当前请求权限码生效大文件上限（如持有 {@code sys:video:upload} 可初始化 2GB 分片上传），
     * 并把生效上限随会话登记，供 complete 阶段取更严格者。</p>
     *
     * @param request 初始化请求：fileName、contentType、size、bucket（可选）、uploadId（可选，续传）
     * @param httpRequest 当前请求（用于解析网关 {@code X-Permission-Codes} 生效上限）
     * @return uploadId、分片大小与已上传分片索引
     */
    @PostMapping("/init")
    @RequirePermission("sys:file:upload")
    public Result<ChunkUploadInitVO> initChunkUpload(@Valid @RequestBody ChunkUploadInitRequest request,
                                                     HttpServletRequest httpRequest) {
        return Result.ok(fileInfoService.initChunkUpload(request, uploadLimits(httpRequest)));
    }

    /**
     * 上传单个分片（同一索引重复上传覆盖）。
     *
     * @param file 分片内容，必填
     * @param uploadId 上传会话 ID
     * @param index 分片索引（从 0 开始）
     * @return 已上传分片索引（升序）
     */
    @PostMapping("/part")
    @RequirePermission("sys:file:upload")
    public Result<List<Integer>> uploadChunkPart(@RequestParam("file") MultipartFile file,
                                                 @RequestParam("uploadId") String uploadId,
                                                 @RequestParam("index") int index) {
        return Result.ok(fileInfoService.uploadChunkPart(uploadId, index,
                toUploadContent(file, fileInfoService.chunkSizeBytes())));
    }

    /**
     * 查询分片上传会话的已上传分片索引。
     *
     * @param uploadId 上传会话 ID
     * @return 已上传分片索引（升序）
     */
    @GetMapping("/{uploadId}/status")
    @RequirePermission("sys:file:upload")
    public Result<List<Integer>> chunkUploadStatus(@PathVariable String uploadId) {
        return Result.ok(fileInfoService.chunkUploadStatus(uploadId));
    }

    /**
     * 完成分片上传：校验完整性后合并落存储并登记元数据。
     *
     * <p>校验上限取「init 阶段登记的生效上限」与「当前请求权限码生效上限」中更严格者，
     * 权限在传输过程中被回收时不会放大上限。</p>
     *
     * @param request 完成请求：uploadId、fileName、size、bucket（可选）
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 与 {@code X-Permission-Codes}）
     * @return 上传结果，与普通上传接口一致
     */
    @PostMapping("/complete")
    @RequirePermission("sys:file:upload")
    public Result<FileUploadVO> completeChunkUpload(@Valid @RequestBody ChunkUploadCompleteRequest request,
                                                    HttpServletRequest httpRequest) {
        return Result.ok(fileInfoService.completeChunkUpload(request,
                UserContext.requireUserId(httpRequest), uploadLimits(httpRequest)));
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
            throw new BizException(ErrorCode.PARAM_ERROR, "分片大小超出上限");
        }
        try {
            return new UploadContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
