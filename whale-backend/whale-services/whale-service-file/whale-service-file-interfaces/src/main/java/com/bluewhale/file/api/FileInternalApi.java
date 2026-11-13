package com.bluewhale.file.api;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.response.FileChangeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

/**
 * 文件服务内网 API 契约（只读 + 归档锁定）。
 *
 * <p>路径版本段位于 {@code internal} 之前（{@code /file/v1/internal/**}），与网关
 * {@code internal-deny} 的版本化内部路径规则对齐，外部统一 403。
 * 内网调用方（whale-agent、whale-archive）需同步配置 {@code whale.file.internal-token}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/internal", version = "1")
public interface FileInternalApi {

    /**
     * 按更新时间游标分页查询文件变更流。
     *
     * @param since 游标下界（不含，ISO-8601 本地时间），缺省为全量
     * @param current 页码，默认 1
     * @param size 每页条数，默认 100、上限 200
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 文件变更分页结果
     */
    @GetMapping("/changes")
    Result<PageResult<FileChangeResponse>> changes(
            @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "100") long size,
            HttpServletRequest httpRequest);

    /**
     * 文件元数据（变更视图，含状态与逻辑删除标记）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 文件变更响应
     */
    @GetMapping("/{id}")
    Result<FileChangeResponse> detail(@PathVariable Long id, HttpServletRequest httpRequest);

    /**
     * 读取文件内容（支持 HTTP Range 分段）。
     *
     * @param id 文件ID
     * @param range 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRange 请求的 {@code If-Range} 头，可为 {@code null}
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 文件内容流式响应
     */
    @GetMapping("/{id}/content")
    ResponseEntity<Resource> content(@PathVariable Long id,
                                     @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                     @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                     HttpServletRequest httpRequest);

    /**
     * 归档锁定文件（状态置 2，幂等）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 空结果
     */
    @PostMapping("/{id}/lock")
    Result<Void> lock(@PathVariable Long id, HttpServletRequest httpRequest);

    /**
     * 解除归档锁定（状态恢复 1，幂等）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于内网来源令牌校验）
     * @return 空结果
     */
    @PostMapping("/{id}/unlock")
    Result<Void> unlock(@PathVariable Long id, HttpServletRequest httpRequest);
}
