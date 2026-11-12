package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.TagStatVO;
import com.bluewhale.forum.service.TagService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 论坛标签接口（F-06-07 标签页）。
 *
 * <p>只做聚合读；按标签筛帖子复用 {@code GET /forum/thread/page?tag=}，
 * 不再单独开一个「标签下帖子」端点——两处查询一旦分叉，可见性判定就会不一致。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * 标签聚合（按帖子数降序）。
     *
     * @param limit 取前 N 个标签（缺省取配置值）
     * @param request 当前请求
     * @return 标签聚合列表
     */
    @GetMapping("/list")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<List<TagStatVO>> list(@RequestParam(value = "limit", required = false) Integer limit,
                                        HttpServletRequest request) {
        return Result.ok(tagService.list(limit, ForumActor.from(request)));
    }
}
