package com.bluewhale.file.assemble;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.model.result.ChunkUploadInitResult;
import com.bluewhale.file.model.result.FileChangeResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FilePresignedResult;
import com.bluewhale.file.model.result.FileQuotaResult;
import com.bluewhale.file.model.result.FileRapidUploadResult;
import com.bluewhale.file.model.result.FileRecycleResult;
import com.bluewhale.file.model.result.FileUploadResult;
import com.bluewhale.file.model.response.ChunkUploadInitResponse;
import com.bluewhale.file.model.response.FileChangeResponse;
import com.bluewhale.file.model.response.FileInfoResponse;
import com.bluewhale.file.model.response.FilePresignedResponse;
import com.bluewhale.file.model.response.FileQuotaResponse;
import com.bluewhale.file.model.response.FileRapidUploadResponse;
import com.bluewhale.file.model.response.FileRecycleResponse;
import com.bluewhale.file.model.response.FileUploadResponse;
import org.mapstruct.Mapper;

/**
 * 文件应用层结果到接口层响应的 MapStruct 装配器。
 *
 * <p>字段同名自动映射；分页结果显式重建以保持 {@link PageResult} 的不可变构造语义。</p>
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface FileAssembler {

    /**
     * 上传结果转响应
     *
     * @param result 上传结果
     * @return 上传响应
     */
    FileUploadResponse toUploadResponse(FileUploadResult result);

    /**
     * 文件信息结果转响应
     *
     * @param result 文件信息结果
     * @return 文件信息响应
     */
    FileInfoResponse toInfoResponse(FileInfoResult result);

    /**
     * 文件变更结果转响应
     *
     * @param result 文件变更结果
     * @return 文件变更响应
     */
    FileChangeResponse toChangeResponse(FileChangeResult result);

    /**
     * 配额结果转响应
     *
     * @param result 配额结果
     * @return 配额响应
     */
    FileQuotaResponse toQuotaResponse(FileQuotaResult result);

    /**
     * 预签名结果转响应
     *
     * @param result 预签名结果
     * @return 预签名响应
     */
    FilePresignedResponse toPresignedResponse(FilePresignedResult result);

    /**
     * 秒传结果转响应
     *
     * @param result 秒传结果
     * @return 秒传响应
     */
    FileRapidUploadResponse toRapidResponse(FileRapidUploadResult result);

    /**
     * 分片上传会话结果转响应
     *
     * @param result 分片上传会话结果
     * @return 分片上传会话响应
     */
    ChunkUploadInitResponse toChunkInitResponse(ChunkUploadInitResult result);

    /**
     * 文件信息分页结果转响应分页
     *
     * @param page 文件信息分页结果
     * @return 文件信息响应分页
     */
    default PageResult<FileInfoResponse> toInfoPage(PageResult<FileInfoResult> page) {
        return PageResult.of(page.getRecords().stream().map(this::toInfoResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 文件变更分页结果转响应分页
     *
     * @param page 文件变更分页结果
     * @return 文件变更响应分页
     */
    default PageResult<FileChangeResponse> toChangePage(PageResult<FileChangeResult> page) {
        return PageResult.of(page.getRecords().stream().map(this::toChangeResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 回收站分页结果转响应分页
     *
     * @param page 回收站分页结果
     * @return 回收站响应分页
     */
    default PageResult<FileRecycleResponse> toRecyclePage(PageResult<FileRecycleResult> page) {
        return PageResult.of(page.getRecords().stream().map(FileRecycleResponse::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
