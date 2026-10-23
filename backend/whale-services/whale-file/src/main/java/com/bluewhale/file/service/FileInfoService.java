package com.bluewhale.file.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件信息服务
 *
 * @author BlueWhale
 */
public interface FileInfoService {

    /**
     * 上传文件：写入存储并登记元数据
     *
     * @param file 上传的文件（必填）
     * @param bucket 目标桶名称；为空时取配置的默认桶
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @return 上传结果视图对象
     */
    FileUploadVO upload(MultipartFile file, String bucket, Long uploaderId);

    /**
     * 秒传（哈希去重）：按 MD5 + 大小匹配已有物理对象，命中时仅登记新元数据、不复制物理对象
     *
     * @param request 秒传请求（文件名、MD5、大小、可选 MIME 类型）
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @return 秒传结果：命中时 {@code hit=true} 且携带新登记的文件元数据，未命中时 {@code hit=false}
     */
    FileRapidUploadVO rapidUpload(FileRapidUploadRequest request, Long uploaderId);

    /**
     * 查询个人存储配额与用量
     *
     * @param uploaderId 上传用户 ID；为 {@code null} 时返回不限配额
     * @return 配额视图对象
     */
    FileQuotaVO quota(Long uploaderId);

    /**
     * 分页查询文件元数据
     *
     * @param request 查询条件
     * @return 文件分页结果
     */
    PageResult<FileInfoVO> page(FileQueryRequest request);

    /**
     * 文件元数据详情
     *
     * @param id 文件ID
     * @return 文件视图对象
     */
    FileInfoVO getDetail(Long id);

    /**
     * 读取文件内容为流式资源
     *
     * @param id 文件ID
     * @return 文件内容资源
     */
    Resource loadResource(Long id);

    /**
     * 删除文件元数据（逻辑删除，物理对象保留）
     *
     * @param id 文件ID
     */
    void delete(Long id);

    /**
     * 生成预签名下载 URL（仅支持预签名能力的对象存储插件可用）
     *
     * @param id 文件ID
     * @param expiresSeconds 有效期（秒）；为空或非正数时取默认 600，超过 3600 截断为 3600
     * @return 预签名 URL 视图对象
     */
    FilePresignedVO presignedUrl(Long id, Integer expiresSeconds);

    /**
     * 初始化分片上传会话
     *
     * @param request 初始化请求（文件名、MIME 类型、总大小、桶、可选续传 uploadId）
     * @return 上传会话信息（uploadId、分片大小、已上传分片索引）
     */
    ChunkUploadInitVO initChunkUpload(ChunkUploadInitRequest request);

    /**
     * 上传单个分片（同索引重复上传覆盖）
     *
     * @param uploadId 上传会话 ID
     * @param index 分片索引（从 0 开始）
     * @param file 分片内容
     * @return 已上传分片索引（升序）
     */
    List<Integer> uploadChunkPart(String uploadId, int index, MultipartFile file);

    /**
     * 查询分片上传会话的已上传分片索引
     *
     * @param uploadId 上传会话 ID
     * @return 已上传分片索引（升序）
     */
    List<Integer> chunkUploadStatus(String uploadId);

    /**
     * 完成分片上传：校验分片完整性后合并落存储并登记元数据
     *
     * @param request 完成请求（uploadId、文件名、总大小、桶）
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @return 上传结果视图对象
     */
    FileUploadVO completeChunkUpload(ChunkUploadCompleteRequest request, Long uploaderId);
}
