package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.service.DiscoverAppService;
import com.bluewhale.video.application.view.HomeView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.application.view.RankingView;
import com.bluewhale.video.interfaces.support.PageParams;
import com.bluewhale.video.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发现接口：首页聚合与站内搜索（浏览权限）。
 *
 * <p>首页聚合走租户级 Redis 缓存（60 秒 + 抖动）；发布 / 下架后主动失效。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}", version = "1")
@RequiredArgsConstructor
public class VideoDiscoverController {

    private final DiscoverAppService discoverAppService;

    /**
     * 首页聚合：最新短视频 / 视频 / 电影 + 分类 + 标签。
     *
     * @param request 当前请求（读取网关注入的用户头）
     * @return 首页视图
     */
    @GetMapping("/home")
    @RequirePermission("sys:video:browse")
    public Result<HomeView> home(HttpServletRequest request) {
        return Result.ok(discoverAppService.home(UserContext.optionalUserId(request)));
    }

    /**
     * 站内搜索；关键词为空时返回稳定的空分页。
     *
     * @param keyword 关键词
     * @param contentType 内容类型，可选
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧分页
     */
    @GetMapping("/search")
    @RequirePermission("sys:video:browse")
    public Result<PageResult<PublicContentView>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        long page = PageParams.current(current, pageNum);
        long pageSizeValue = PageParams.size(size, pageSize);
        return Result.ok(discoverAppService.search(keyword, page, pageSizeValue,
                UserContext.optionalUserId(request)));
    }

    /**
     * 发现榜单：热门榜与新片榜（可选内容类型）。
     *
     * @param type 内容类型：SHORT_VIDEO / VIDEO / MOVIE，可选（空白表示跨类型）
     * @param size 每榜条数，可选（默认 10，上限 50）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 榜单视图
     */
    @GetMapping("/rankings")
    @RequirePermission("sys:video:browse")
    public Result<RankingView> rankings(@RequestParam(value = "type", required = false) String type,
                                        @RequestParam(value = "size", required = false) Long size,
                                        HttpServletRequest request) {
        long limit = size == null ? 0L : size;
        return Result.ok(discoverAppService.rankings(type, limit, UserContext.optionalUserId(request)));
    }
}
