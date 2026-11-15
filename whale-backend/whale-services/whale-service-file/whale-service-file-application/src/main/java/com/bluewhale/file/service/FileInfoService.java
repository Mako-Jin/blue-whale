package com.bluewhale.file.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.ChunkUploadCompleteCommand;
import com.bluewhale.file.model.command.ChunkUploadInitCommand;
import com.bluewhale.file.model.command.RapidUploadCommand;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FileChangesQuery;
import com.bluewhale.file.model.query.FilePageQuery;
import com.bluewhale.file.model.query.FileRecyclePageQuery;
import com.bluewhale.file.model.result.ChunkUploadInitResult;
import com.bluewhale.file.model.result.FileChangeResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FilePresignedResult;
import com.bluewhale.file.model.result.FileQuotaResult;
import com.bluewhale.file.model.result.FileRapidUploadResult;
import com.bluewhale.file.model.result.FileRecycleResult;
import com.bluewhale.file.model.result.FileUploadResult;

import java.io.InputStream;
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
     * @param command 上传命令（内容、桶、上传人、生效上限）
     * @return 上传结果
     */
    FileUploadResult upload(UploadFileCommand command);

    /**
     * 秒传（哈希去重）：按 MD5 + 大小匹配已有物理对象，命中时仅登记新元数据、不复制物理对象
     *
     * @param command 秒传命令（文件名、MD5、大小、MIME、上传人、生效上限）
     * @return 秒传结果：命中时 {@code hit=true} 且携带新登记的文件元数据
     */
    FileRapidUploadResult rapidUpload(RapidUploadCommand command);

    /**
     * 查询个人存储配额与用量
     *
     * @param uploaderId 上传用户 ID；为 {@code null} 时返回不限配额
     * @param limits 生效的上传上限与配额；为 {@code null} 时使用全局默认值
     * @return 配额结果
     */
    FileQuotaResult quota(Long uploaderId, UploadLimits limits);

    /**
     * 分页查询文件元数据
     *
     * <p>默认仅返回调用者本人上传的文件；持有文件管理权限时可查看全租户文件。</p>
     *
     * @param query 查询条件与调用者可见范围
     * @return 文件分页结果
     */
    PageResult<FileInfoResult> page(FilePageQuery query);

    /**
     * 文件元数据详情
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 文件信息结果
     */
    FileInfoResult getDetail(Long id, Long requesterId, boolean canManage);

    /**
     * 文件变更详情（内网接口专用）：含状态、逻辑删除标记与更新时间，
     * 供 whale-agent 知识库单文件重建判定可采集性。
     *
     * <p>与变更流语义一致：已逻辑删除的文件仍可查询，并以 {@code deleted=1} 标记。</p>
     *
     * @param id 文件ID
     * @return 文件变更结果
     */
    FileChangeResult getChangeDetail(Long id);

    /**
     * 按更新时间游标分页查询文件变更流（内网接口专用）
     *
     * <p>排序固定为 {@code update_time ASC, id ASC}，调用方以返回记录的最大
     * {@code updateTime} 作为下一轮游标；已逻辑删除的文件仍会出现在变更流中，
     * 并以 {@code deleted=1} 标记，供下游移除索引。</p>
     *
     * @param query 变更流查询（游标与分页）
     * @return 文件变更分页结果
     */
    PageResult<FileChangeResult> changes(FileChangesQuery query);

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
     * 分页查询回收站文件（已逻辑删除）
     *
     * <p>无管理权限时仅返回本人上传的文件；管理权限可查看全租户回收站。</p>
     *
     * @param query 回收站查询条件与调用者可见范围
     * @return 回收站分页结果
     */
    PageResult<FileRecycleResult> recyclePage(FileRecyclePageQuery query);

    /**
     * 恢复回收站文件：重新占用个人配额并清除逻辑删除标记
     *
     * <p>幂等：文件已恢复或不存在时按成功返回。超出配额时抛 {@code PARAM_ERROR}。</p>
     *
     * @param id 文件ID
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @param limits 生效的上传上限与配额；为 {@code null} 时使用全局默认配额
     */
    void restore(Long id, Long requesterId, boolean canManage, UploadLimits limits);

    /**
     * 生成预签名下载 URL（仅支持预签名能力的对象存储插件可用）
     *
     * @param id 文件ID
     * @param expiresSeconds 有效期（秒）；为空或非正数时取默认 600，超过 3600 截断为 3600
     * @param requesterId 调用者用户 ID
     * @param canManage 是否持有文件管理权限
     * @return 预签名结果
     */
    FilePresignedResult presignedUrl(Long id, Integer expiresSeconds, Long requesterId, boolean canManage);

    /**
     * 初始化分片上传会话，并把生效上限随会话元数据登记，
     * 供 complete 阶段与当前上限取更严格者，防止两阶段绕过
     *
     * @param command 初始化命令（文件名、MIME 类型、总大小、桶、可选续传 uploadId、生效上限）
     * @return 上传会话信息（uploadId、分片大小、已上传分片索引）
     */
    ChunkUploadInitResult initChunkUpload(ChunkUploadInitCommand command);

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
     * @param command 完成命令（uploadId、文件名、总大小、桶、上传人、生效上限）
     * @return 上传结果
     */
    FileUploadResult completeChunkUpload(ChunkUploadCompleteCommand command);
}
