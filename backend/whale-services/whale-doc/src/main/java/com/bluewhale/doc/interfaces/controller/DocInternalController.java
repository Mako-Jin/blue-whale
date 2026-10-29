package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocInternalService;
import com.bluewhale.doc.interfaces.vo.DocInternalChangeVO;
import com.bluewhale.doc.interfaces.vo.DocInternalPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 文档服务内网接口（知识源增量同步）。
 *
 * <p>服务内路径前缀 {@code /doc/internal}；网关 {@code internal-deny} 对
 * {@code /doc/internal/**} 统一返回 403，外部不可达，故不加权限码注解。
 * 租户来自网关注入的 {@code X-Tenant-Id}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc/internal/page")
@RequiredArgsConstructor
public class DocInternalController {

    private final DocInternalService internalService;

    /**
     * 查询公开已发布页面的增量变更。
     *
     * @param since   更新时间游标（不含，ISO-8601 本地时间）
     * @param current 当前页码
     * @param size    每页条数
     * @return 变更分页
     */
    @GetMapping("/changes")
    public Result<PageResult<DocInternalChangeVO>> changes(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "100") long size) {
        return Result.ok(internalService.changes(since, current, size));
    }

    /**
     * 读取公开已发布页面快照。
     *
     * @param id 页面ID
     * @return 页面快照
     */
    @GetMapping("/{id}")
    public Result<DocInternalPageVO> detail(@PathVariable Long id) {
        return Result.ok(internalService.detail(id));
    }
}
