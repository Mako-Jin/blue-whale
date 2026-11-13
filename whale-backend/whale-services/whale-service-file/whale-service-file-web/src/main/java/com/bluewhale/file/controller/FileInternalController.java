package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.api.FileInternalApi;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FileChangesQuery;
import com.bluewhale.file.model.response.FileChangeResponse;
import com.bluewhale.file.model.response.FileUploadResponse;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileUploadResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.support.FileContentResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 文件内网接口实现（Web 适配层）：内网上传、变更流与归档锁定。
 *
 * <p>服务内路径前缀 {@code /file/v1/internal}，供 whale-agent（知识库采集）与
 * whale-archive（归档锁定）调用；网关 {@code internal-deny} 统一返回 403，外部不可达。
 * 内网来源校验由 common starter 的 {@code InternalTokenFilter} 统一拦截整段
 * {@code /**}{@code /internal/**}（配置前缀 {@code bluewhale.internal-token}，默认开启，
 * 非 dev 环境缺少令牌时启动失败），本类不再逐个方法手工校验。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class FileInternalController implements FileInternalApi {

    private final FileInfoService fileInfoService;

    private final FileUploadPolicy fileUploadPolicy;

    private final FileAssembler fileAssembler;

    @Override
    public Result<PageResult<FileChangeResponse>> changes(
            @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "100") long size,
            HttpServletRequest httpRequest) {
        return Result.ok(fileAssembler.toChangePage(
                fileInfoService.changes(new FileChangesQuery(since, current, size))));
    }

    @Override
    public Result<FileChangeResponse> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(fileAssembler.toChangeResponse(fileInfoService.getChangeDetail(id)));
    }

    @Override
    public ResponseEntity<Resource> content(@PathVariable Long id,
                                            @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                            String range,
                                            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false)
                                            String ifRange,
                                            HttpServletRequest httpRequest) {
        FileInfoResult info = fileInfoService.getDetail(id, null, true);
        return FileContentResponses.build(fileInfoService, info, null, true, false, range, ifRange);
    }

    @Override
    public Result<Void> lock(@PathVariable Long id, HttpServletRequest httpRequest) {
        fileInfoService.lock(id);
        return Result.ok();
    }

    @Override
    public Result<Void> unlock(@PathVariable Long id, HttpServletRequest httpRequest) {
        fileInfoService.unlock(id);
        return Result.ok();
    }

    @Override
    public Result<FileUploadResponse> internalUpload(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "bucket", required = false)
                                                     String bucket,
                                                     HttpServletRequest httpRequest) {
        UploadLimits limits = fileUploadPolicy.defaults();
        UploadContent content = toUploadContent(file, limits.maxSizeBytes());
        FileUploadResult result = fileInfoService.upload(new UploadFileCommand(content, bucket,
                UserContext.findUserId(httpRequest), limits));
        return Result.ok(fileAssembler.toUploadResponse(result));
    }

    /**
     * 将 Web 上传对象适配为应用层中立上传内容（Web 类型仅停留在接口层）。
     *
     * <p>先按 {@code MultipartFile.getSize()} 预检，超限时直接拒绝并提示走分片上传，
     * 避免大文件 {@code getBytes()} 全量进入堆内存导致 OOM。</p>
     *
     * @param file 上传的文件
     * @param maxBytes 允许全量读入的最大字节数
     * @return 上传内容
     * @throws BizException 超出全量读入上限或读取内容失败时抛出
     */
    private static UploadContent toUploadContent(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "文件大小超出普通上传上限，请使用分片上传接口");
        }
        try {
            return new UploadContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
