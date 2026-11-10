package com.bluewhale.file.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.support.FileContentResponses;
import com.bluewhale.file.vo.FileChangeVO;
import com.bluewhale.file.vo.FileInfoVO;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 文件服务内网接口（只读 + 归档锁定）。
 *
 * <p>服务内路径前缀 {@code /file/internal}，供 whale-agent（知识库采集）与 whale-archive（归档锁定）调用；
 * 网关 {@code internal-deny} 对 {@code /file/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/file/internal")
@RequiredArgsConstructor
public class FileInternalController {

    private final FileInfoService fileInfoService;

    /**
     * 按更新时间游标分页查询文件变更流。
     *
     * @param since 游标下界（不含，ISO-8601 本地时间）
     * @param current 页码，默认 1
     * @param size 每页条数，默认 100、上限 200
     * @return 文件变更分页结果
     */
    @GetMapping("/changes")
    public Result<PageResult<FileChangeVO>> changes(
            @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "100") long size) {
        return Result.ok(fileInfoService.changes(since, current, size));
    }

    /**
     * 文件元数据（变更视图，含状态与逻辑删除标记，供知识库采集判定）。
     *
     * @param id 文件ID
     * @return 文件变更视图对象
     */
    @GetMapping("/{id}")
    public Result<FileChangeVO> detail(@PathVariable Long id) {
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
                                            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange) {
        FileInfoVO info = fileInfoService.getDetail(id, null, true);
        return FileContentResponses.build(fileInfoService, info, null, true, false, range, ifRange);
    }

    /**
     * 归档锁定文件（状态置 2，幂等）。
     *
     * @param id 文件ID
     * @return 空结果
     */
    @PostMapping("/{id}/lock")
    public Result<Void> lock(@PathVariable Long id) {
        fileInfoService.lock(id);
        return Result.ok();
    }

    /**
     * 解除归档锁定（状态恢复 1，幂等）。
     *
     * @param id 文件ID
     * @return 空结果
     */
    @PostMapping("/{id}/unlock")
    public Result<Void> unlock(@PathVariable Long id) {
        fileInfoService.unlock(id);
        return Result.ok();
    }
}
