package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.MovieQueryCommand;
import com.bluewhale.video.application.service.MovieAppService;
import com.bluewhale.video.application.view.MovieDetailView;
import com.bluewhale.video.application.view.MovieView;
import com.bluewhale.video.interfaces.support.PageParams;
import com.bluewhale.video.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电影用户侧接口：电影列表与详情（含海报 / 背景图代理地址与收录片单）。
 *
 * <p>仅返回已发布且当前用户可见的电影；不暴露任何文件 ID 与授权细节。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}", version = "1")
@RequiredArgsConstructor
public class VideoMovieController {

    private final MovieAppService movieAppService;

    /**
     * 电影分页（仅已发布且可见内容）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 电影分页
     */
    @GetMapping("/movies/page")
    @RequirePermission("sys:video:browse")
    public Result<PageResult<MovieView>> page(
            @ModelAttribute MovieQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(movieAppService.publicPage(command, UserContext.optionalUserId(request)));
    }

    /**
     * 电影详情（不可见内容按不存在处理）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 电影详情
     */
    @GetMapping("/movies/{id}")
    @RequirePermission("sys:video:browse")
    public Result<MovieDetailView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(movieAppService.detail(id, UserContext.optionalUserId(request)));
    }
}
