package com.bluewhale.file.controller;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.support.FileContentResponses;
import com.bluewhale.file.vo.FileChangeVO;
import com.bluewhale.file.vo.FileInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * 文件服务内网接口（只读 + 归档锁定）。
 *
 * <p>服务内路径前缀 {@code /file/internal}，供 whale-agent（知识库采集）与 whale-archive（归档锁定）调用；
 * 网关 {@code internal-deny} 对 {@code /file/internal/**} 统一返回 403，外部不可达，故不加权限码注解。
 * 网关屏蔽是部署侧配置而非代码侧保证，因此叠加第二道防线：配置
 * {@code whale.file.internal-token} 非空时，所有内网接口必须携带匹配的 {@code X-Internal-Token}
 * 请求头（常量时间比对），否则 403。调用方（whale-agent / whale-archive）需同步配置该令牌。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/file/internal")
@RequiredArgsConstructor
public class FileInternalController {

    /** 内网来源校验令牌请求头 */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final FileInfoService fileInfoService;

    private final FileProperties fileProperties;

    /**
     * 按更新时间游标分页查询文件变更流。
     *
     * @param since 游标下界（不含，ISO-8601 本地时间），缺省为全量
     * @param current 页码，默认 1
     * @param size 每页条数，默认 100、上限 200
     * @return 文件变更分页结果
     */
    @GetMapping("/changes")
    public Result<PageResult<FileChangeVO>> changes(
            @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "100") long size,
            HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        return Result.ok(fileInfoService.changes(since, current, size));
    }

    /**
     * 文件元数据（变更视图，含状态与逻辑删除标记，供知识库采集判定）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 文件变更视图对象
     */
    @GetMapping("/{id}")
    public Result<FileChangeVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        return Result.ok(fileInfoService.getChangeDetail(id));
    }

    /**
     * 读取文件内容（知识抽取用流式响应，支持 HTTP Range 分段）。
     *
     * <p>内网服务间调用，按系统级访问（不做归属校验），保持内网通道原有可见范围；
     * 音频播放等服务可通过 {@code Range} 头按区间拉取，返回 206 与 {@code Content-Range}。</p>
     *
     * @param id 文件ID
     * @param range 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRange 请求的 {@code If-Range} 头，可为 {@code null}
     * @return 文件内容流式响应
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id,
                                            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                            HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        FileInfoVO info = fileInfoService.getDetail(id, null, true);
        return FileContentResponses.build(fileInfoService, info, null, true, false, range, ifRange);
    }

    /**
     * 归档锁定文件（状态置 2，幂等）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 空结果
     */
    @PostMapping("/{id}/lock")
    public Result<Void> lock(@PathVariable Long id, HttpServletRequest httpRequest) {
        ensureInternalToken(httpRequest);
        fileInfoService.lock(id);
        return Result.ok();
    }

    /**
     * 解除归档锁定（状态恢复 1，幂等）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 空结果
     */
    @PostMapping("/{id}/unlock")
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
