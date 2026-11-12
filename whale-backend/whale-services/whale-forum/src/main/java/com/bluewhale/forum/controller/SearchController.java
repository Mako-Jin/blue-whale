package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.SearchStatusVO;
import com.bluewhale.forum.dto.ThreadListItemVO;
import com.bluewhale.forum.dto.ThreadQuery;
import com.bluewhale.forum.service.SearchService;
import com.bluewhale.forum.service.ThreadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论坛检索接口。
 *
 * <p>检索结果必须与列表页同一套可见性判定，因此查询委托给 {@link ThreadService#search}
 * 而不是在这里直连 Mapper：一旦检索绕过版块可见性与状态过滤，受限版块的内容就能
 * 被关键词捞出来——这类越权不会报错，只会安静地泄露。</p>
 *
 * <p>{@code /status} 与 {@code /reindex} 是运维动作：前者只读自查（任何能浏览论坛的人
 * 都该看到「索引覆盖到哪」），后者会批量改数据，只给 {@code manage:ops}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum/search")
@RequiredArgsConstructor
public class SearchController {

    private final ThreadService threadService;

    private final SearchService searchService;

    /**
     * 关键词检索。
     *
     * @param keyword 关键词（必填，空白按未命中处理）
     * @param boardId 限定版块（可空表示全部可见版块）
     * @param essenceOnly 只看精华
     * @param current 页码
     * @param size 每页条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<PageResult<ThreadListItemVO>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "boardId", required = false) Long boardId,
            @RequestParam(value = "essenceOnly", required = false) Boolean essenceOnly,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "size", required = false) Long size,
            HttpServletRequest request) {
        ThreadQuery query = new ThreadQuery();
        query.setKeyword(keyword);
        query.setBoardId(boardId);
        query.setEssenceOnly(essenceOnly);
        query.setCurrent(current);
        query.setSize(size);
        return Result.ok(threadService.search(query, ForumActor.from(request)));
    }

    /**
     * 检索状态（引擎、分词长度、索引覆盖条数）。
     *
     * @return 状态
     */
    @GetMapping("/status")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<SearchStatusVO> status() {
        return Result.ok(searchService.status());
    }

    /**
     * 重建检索索引（回填 {@code content_text}，可重复执行）。
     *
     * @param limit 本次最多回填条数
     * @return 实际回填条数
     */
    @PostMapping("/reindex")
    @RequirePermission(ForumPermissions.MANAGE_OPS)
    @AuditLog(operation = "重建论坛检索索引", type = OperateType.UPDATE, targetType = "fm_thread")
    public Result<Integer> reindex(@RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(searchService.reindex(limit));
    }
}
