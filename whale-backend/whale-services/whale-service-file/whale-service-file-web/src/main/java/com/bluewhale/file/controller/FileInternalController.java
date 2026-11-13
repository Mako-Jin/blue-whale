package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.api.FileInternalApi;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.query.FileChangesQuery;
import com.bluewhale.file.model.response.FileChangeResponse;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.support.FileContentResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 文件内网接口实现（Web 适配层）：只读变更流与归档锁定。
 *
 * <p>服务内路径前缀 {@code /file/v1/internal}，供 whale-agent（知识库采集）与
 * whale-archive（归档锁定）调用；网关 {@code internal-deny} 统一返回 403，外部不可达，
 * 故不加权限码注解。网关屏蔽是部署侧配置而非代码侧保证，因此叠加第二道防线：配置
 * {@code whale.file.internal-token} 非空时，所有内网接口必须携带匹配的
 * {@code X-Internal-Token} 请求头（常量时间比对），否则 403。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class FileInternalController implements FileInternalApi {

    /** 内网来源校验令牌请求头 */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final FileInfoService fileInfoService;

    private final FileProperties fileProperties;

    private final FileAssembler fileAssembler;

    @Override
    public Result<PageResult<FileChangeResponse>> changes(
            @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "100") long size,
            HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        return Result.ok(fileAssembler.toChangePage(
                fileInfoService.changes(new FileChangesQuery(since, current, size))));
    }

    @Override
    public Result<FileChangeResponse> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        return Result.ok(fileAssembler.toChangeResponse(fileInfoService.getChangeDetail(id)));
    }

    @Override
    public ResponseEntity<Resource> content(@PathVariable Long id,
                                            @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                            String range,
                                            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false)
                                            String ifRange,
                                            HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        FileInfoResult info = fileInfoService.getDetail(id, null, true);
        return FileContentResponses.build(fileInfoService, info, null, true, false, range, ifRange);
    }

    @Override
    public Result<Void> lock(@PathVariable Long id, HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        fileInfoService.lock(id);
        return Result.ok();
    }

    @Override
    public Result<Void> unlock(@PathVariable Long id, HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        fileInfoService.unlock(id);
        return Result.ok();
    }

    /**
     * 校验内网来源令牌：{@code whale.file.internal-token} 非空时，
     * 请求必须携带匹配的 {@code X-Internal-Token}；缺失或不匹配返回 403。
     *
     * <p>使用 {@link MessageDigest#isEqual} 常量时间比对，避免时序侧信道枚举令牌。</p>
     *
     * @param httpRequest 当前请求
     */
    private void ensureInternalToken(HttpServletRequest httpRequest) {
        String expected = fileProperties.getInternalToken();
        if (!StringUtils.hasText(expected)) {
            return;
        }
        String provided = httpRequest.getHeader(INTERNAL_TOKEN_HEADER);
        boolean matched = provided != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
