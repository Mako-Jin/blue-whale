package com.bluewhale.file.api;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.request.ChunkUploadCompleteRequest;
import com.bluewhale.file.model.request.ChunkUploadInitRequest;
import com.bluewhale.file.model.response.ChunkUploadInitResponse;
import com.bluewhale.file.model.response.FileUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件分片上传 API 契约（G1 大文件上传主通道）。
 *
 * <p>路径版本化为 {@code /file/v1/upload/chunk/**}，按网关可信注入的
 * {@code X-Permission-Codes} 解析生效上限与配额；complete 阶段取「init 登记上限」
 * 与「当前请求上限」中更严格者，权限在传输过程中被回收时不会放大上限。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/upload/chunk", version = "1")
public interface FileChunkUploadApi {

    /**
     * 初始化分片上传会话（支持传入既有 uploadId 续传）。
     *
     * @param request 初始化请求
     * @param httpRequest 当前请求（用于解析网关 {@code X-Permission-Codes} 生效上限）
     * @return uploadId、分片大小与已上传分片索引
     */
    @PostMapping("/init")
    Result<ChunkUploadInitResponse> initChunkUpload(@Valid @RequestBody ChunkUploadInitRequest request,
                                                    HttpServletRequest httpRequest);

    /**
     * 上传单个分片（同一索引重复上传覆盖）。
     *
     * @param file 分片内容，必填
     * @param uploadId 上传会话 ID
     * @param index 分片索引（从 0 开始）
     * @return 已上传分片索引（升序）
     */
    @PostMapping("/part")
    Result<List<Integer>> uploadChunkPart(@RequestParam("file") MultipartFile file,
                                          @RequestParam("uploadId") String uploadId,
                                          @RequestParam("index") int index);

    /**
     * 查询分片上传会话的已上传分片索引。
     *
     * @param uploadId 上传会话 ID
     * @return 已上传分片索引（升序）
     */
    @GetMapping("/{uploadId}/status")
    Result<List<Integer>> chunkUploadStatus(@PathVariable String uploadId);

    /**
     * 完成分片上传：校验完整性后合并落存储并登记元数据。
     *
     * @param request 完成请求
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 与 {@code X-Permission-Codes}）
     * @return 上传结果，与普通上传接口一致
     */
    @PostMapping("/complete")
    Result<FileUploadResponse> completeChunkUpload(@Valid @RequestBody ChunkUploadCompleteRequest request,
                                                   HttpServletRequest httpRequest);
}
