package com.bluewhale.file.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.service.model.UploadContent;
import com.bluewhale.file.service.model.UploadLimits;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileChangeVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;

import java.io.InputStream;
import java.time.LocalDateTime;
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
     * @param content 上传内容（必填）
     * @param bucket 目标桶名称；为空时取配置的默认桶
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @return 上传结果视图对象
     */
    FileUploadVO upload(UploadContent content, String bucket, Long uploaderId);

    /**
     * 上传文件（按生效上限）：写入存储并登记元数据
     *
     * @param content 上传内容（必填）
     * @param bucket 目标桶名称；为空时取配置的默认桶
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @param limits 生效的上传上限与配额；为 {@code null} 时使用全局默认值
     * @return 上传结果视图对象
     */
    FileUploadVO upload(UploadContent content, String bucket, Long uploaderId, UploadLimits limits);

    /**
     * 秒传（哈希去重）：按 MD5 + 大小匹配已有物理对象，命中时仅登记新元数据、不复制物理对象
     *
     * @param request 秒传请求（文件名、MD5、大小、可选 MIME 类型）
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @return 秒传结果：命中时 {@code hit=true} 且携带新登记的文件元数据，未命中时 {@code hit=false}
     */
    FileRapidUploadVO rapidUpload(FileRapidUploadRequest request, Long uploaderId);

    /**
     * 秒传（哈希去重，按生效上限）
     *
     * @param request 秒传请求（文件名、MD5、大小、可选 MIME 类型）
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @param limits 生效的上传上限与配额；为 {@code null} 时使用全局默认值
     * @return 秒传结果：命中时 {@code hit=true} 且携带新登记的文件元数据，未命中时 {@code hit=false}
     */
    FileRapidUploadVO rapidUpload(FileRapidUploadRequest request, Long uploaderId, UploadLimits limits);

    /**
     * 查询个人存储配额与用量
     *
     * @param uploaderId 上传用户 ID；为 {@code null} 时返回不限配额
     * @return 配额视图对象
     */
    FileQuotaVO quota(Long uploaderId);

    /**
     * 查询个人存储配额与用量（按生效配额）
     *
     * @param uploaderId 上传用户 ID；为 {@code null} 时返回不限配额
     * @param limits 生效的上传上限与配额；为 {@code null} 时使用全局默认值
     * @return 配额视图对象
     */
    FileQuotaVO quota(Long uploaderId, UploadLimits limits);

    /**
     * 分页查询文件元数据
     *
     * <p>默认仅返回调用者本人上传的文件；持有文件管理权限时可查看全租户文件。</p>
     *
     * @param request 查询条件
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限（可查看全租户文件）
     * @return 文件分页结果
     */
    PageResult<FileInfoVO> page(FileQueryRequest request, Long requesterId, boolean canManage);

    /**
     * 文件元数据详情
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 文件视图对象
     */
    FileInfoVO getDetail(Long id, Long requesterId, boolean canManage);

    /**
     * 文件变更视图详情（内网接口专用）：含状态、逻辑删除标记与更新时间，
     * 供 whale-agent 知识库单文件重建判定可采集性。
     *
     * <p>与变更流语义一致：已逻辑删除的文件仍可查询，并以 {@code deleted=1} 标记。</p>
     *
     * @param id 文件ID
     * @return 文件变更视图对象
     */
    FileChangeVO getChangeDetail(Long id);

    /**
     * 按更新时间游标分页查询文件变更流（内网接口专用）
     *
     * <p>排序固定为 {@code update_time ASC, id ASC}，调用方以返回记录的最大
     * {@code updateTime} 作为下一轮游标；已逻辑删除的文件仍会出现在变更流中，
     * 并以 {@code deleted=1} 标记，供下游移除索引。</p>
     *
     * @param since 游标下界（不含），可为 {@code null} 表示不限
     * @param current 页码（小于 1 时按 1 处理）
     * @param size 每页条数（1..200，越界自动收敛）
     * @return 文件变更分页结果
     */
    PageResult<FileChangeVO> changes(LocalDateTime since, long current, long size);

    /**
     * 锁定文件（归档不可变）：状态置 {@code 2 归档锁定}
     *
     * <p>幂等：已锁定文件再次锁定不报错；已禁用文件不可锁定。</p>
     *
     * @param id 文件ID
     */
    void lock(Long id);

    /**
     * 解锁文件：状态由 {@code 2 归档锁定} 恢复为 {@code 1 正常}
     *
     * <p>幂等：未锁定文件调用不报错且状态不变。</p>
     *
     * @param id 文件ID
     */
    void unlock(Long id);

    /**
     * 读取文件内容为输入流（由调用方负责关闭）
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 文件内容输入流
     */
    InputStream openContent(Long id, Long requesterId, boolean canManage);

    /**
     * 按字节区间读取文件内容（语义对齐 HTTP Range，闭区间）
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @param offset 起始偏移（字节，从 0 开始）
     * @param length 读取长度（字节）
     * @return 仅包含请求区间的内容输入流，由调用方负责关闭
     */
    InputStream openContent(Long id, Long requesterId, boolean canManage, long offset, long length);

    /**
     * 删除文件元数据（逻辑删除，物理对象保留）
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     */
    void delete(Long id, Long requesterId, boolean canManage);

    /**
     * 生成预签名下载 URL（仅支持预签名能力的对象存储插件可用）
     *
     * @param id 文件ID
     * @param expiresSeconds 有效期（秒）；为空或非正数时取默认 600，超过 3600 截断为 3600
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 预签名 URL 视图对象
     */
    FilePresignedVO presignedUrl(Long id, Integer expiresSeconds, Long requesterId, boolean canManage);

    /**
     * 初始化分片上传会话
     *
     * @param request 初始化请求（文件名、MIME 类型、总大小、桶、可选续传 uploadId）
     * @return 上传会话信息（uploadId、分片大小、已上传分片索引）
     */
    ChunkUploadInitVO initChunkUpload(ChunkUploadInitRequest request);

    /**
     * 初始化分片上传会话（按生效上限），并把生效上限随会话元数据登记，
     * 供 complete 阶段与当前上限取更严格者，防止两阶段绕过
     *
     * @param request 初始化请求（文件名、MIME 类型、总大小、桶、可选续传 uploadId）
     * @param limits 生效的上传上限与配额；为 {@code null} 时使用全局默认值
     * @return 上传会话信息（uploadId、分片大小、已上传分片索引）
     */
    ChunkUploadInitVO initChunkUpload(ChunkUploadInitRequest request, UploadLimits limits);

    /**
     * 生效的分片上传单片大小（{@code whale.file.chunk-size-bytes}，缺省 5MB）
     *
     * @return 单片最大字节数
     */
    long chunkSizeBytes();

    /**
     * 上传单个分片（同索引重复上传覆盖）
     *
     * @param uploadId 上传会话 ID
     * @param index 分片索引（从 0 开始）
     * @param part 分片内容
     * @return 已上传分片索引（升序）
     */
    List<Integer> uploadChunkPart(String uploadId, int index, UploadContent part);

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

    /**
     * 完成分片上传（按生效上限）：校验分片完整性后合并落存储并登记元数据
     *
     * @param request 完成请求（uploadId、文件名、总大小、桶）
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @param limits 完成请求当前生效的上传上限与配额；为 {@code null} 时使用全局默认值。
     *               实际校验取「该值」与「init 阶段登记上限」中更严格者
     * @return 上传结果视图对象
     */
    FileUploadVO completeChunkUpload(ChunkUploadCompleteRequest request, Long uploaderId, UploadLimits limits);
}
