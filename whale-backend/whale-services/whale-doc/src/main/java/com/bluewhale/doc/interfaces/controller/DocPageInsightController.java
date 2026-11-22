package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocPageActivityService;
import com.bluewhale.doc.application.service.DocPageLinkService;
import com.bluewhale.doc.application.service.DocPageRecentService;
import com.bluewhale.doc.interfaces.vo.DocActivityVO;
import com.bluewhale.doc.interfaces.vo.DocBacklinkVO;
import com.bluewhale.doc.interfaces.vo.DocRecentVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 页面洞察接口：动态、反向链接与最近访问。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}", version = "1")
@RequiredArgsConstructor
public class DocPageInsightController {

    private final DocPageActivityService activityService;

    private final DocPageLinkService linkService;

    private final DocPageRecentService recentService;

    private final DocAccessGuard accessGuard;

    /**
     * 分页查询页面动态。
     *
     * @param pageId      页面ID
     * @param current     当前页
     * @param size        每页大小
     * @param httpRequest 当前请求
     * @return 动态分页
     */
    @GetMapping("/page/{pageId}/activity")
    @RequirePermission("sys:doc:page:read")
    public Result<PageResult<DocActivityVO>> activity(@PathVariable Long pageId,
                                                      @RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      HttpServletRequest httpRequest) {
        Long userId = UserContext.requireUserId(httpRequest);
        accessGuard.requirePageRead(pageId, userId);
        return Result.ok(activityService.page(pageId, current, size));
    }

    /**
     * 查询页面反向链接。
     *
     * @param pageId      页面ID
     * @param httpRequest 当前请求
     * @return 反向链接列表
     */
    @GetMapping("/page/{pageId}/backlink")
    @RequirePermission("sys:doc:page:read")
    public Result<List<DocBacklinkVO>> backlinks(@PathVariable Long pageId, HttpServletRequest httpRequest) {
        Long userId = UserContext.requireUserId(httpRequest);
        accessGuard.requirePageRead(pageId, userId);
        return Result.ok(linkService.backlinks(pageId, userId));
    }

    /**
     * 查询我的最近访问。
     *
     * @param limit       条数
     * @param httpRequest 当前请求
     * @return 最近访问列表
     */
    @GetMapping("/recent")
    @RequirePermission("sys:doc:page:read")
    public Result<List<DocRecentVO>> recent(@RequestParam(required = false) Integer limit,
                                            HttpServletRequest httpRequest) {
        return Result.ok(recentService.recent(UserContext.requireUserId(httpRequest), limit));
    }
}
