package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.command.ContentQueryCommand;
import com.bluewhale.video.application.command.ContentSaveCommand;
import com.bluewhale.video.application.service.ContentAppService;
import com.bluewhale.video.application.service.DiscoverAppService;
import com.bluewhale.video.application.view.ContentDetailView;
import com.bluewhale.video.application.view.ContentView;
import com.bluewhale.video.application.view.FeedView;
import com.bluewhale.video.application.view.OwnerContentView;
import com.bluewhale.video.application.view.PublicContentView;
import com.bluewhale.video.interfaces.support.PageParams;
import com.bluewhale.video.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容用户侧接口：短视频 / 视频草稿创建与维护、我的作品、内容详情与列表。
 *
 * <p>上传前须签署当前版本上传声明；写操作要求 {@code sys:video:upload}，
 * 浏览要求 {@code sys:video:browse}。电影仅运营入库，不对用户开放创建。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}", version = "1")
@RequiredArgsConstructor
public class VideoContentController {

    /** 短视频类型 */
    private static final String TYPE_SHORT_VIDEO = "SHORT_VIDEO";

    /** 视频类型 */
    private static final String TYPE_VIDEO = "VIDEO";

    private final ContentAppService contentAppService;

    private final DiscoverAppService discoverAppService;

    /**
     * 创建短视频草稿。
     *
     * @param command 保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 内容 ID
     */
    @PostMapping("/user/shorts")
    @RequirePermission("sys:video:upload")
    public Result<Long> createShort(@Valid @RequestBody ContentSaveCommand command, HttpServletRequest request) {
        command.setContentType(TYPE_SHORT_VIDEO);
        ContentView view = contentAppService.create(command, UserContext.requireUserId(request));
        return Result.ok(view.getId());
    }

    /**
     * 创建视频草稿。
     *
     * @param command 保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 内容 ID
     */
    @PostMapping("/user/videos")
    @RequirePermission("sys:video:upload")
    public Result<Long> createVideo(@Valid @RequestBody ContentSaveCommand command, HttpServletRequest request) {
        command.setContentType(TYPE_VIDEO);
        ContentView view = contentAppService.create(command, UserContext.requireUserId(request));
        return Result.ok(view.getId());
    }

    /**
     * 更新本人短视频。
     *
     * @param id 内容 ID
     * @param command 保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PutMapping("/user/shorts/{id}")
    @RequirePermission("sys:video:upload")
    public Result<Void> updateShort(@PathVariable Long id, @Valid @RequestBody ContentSaveCommand command,
                                    HttpServletRequest request) {
        contentAppService.update(id, command, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 更新本人视频。
     *
     * @param id 内容 ID
     * @param command 保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PutMapping("/user/videos/{id}")
    @RequirePermission("sys:video:upload")
    public Result<Void> updateVideo(@PathVariable Long id, @Valid @RequestBody ContentSaveCommand command,
                                    HttpServletRequest request) {
        contentAppService.update(id, command, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 删除本人短视频。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/user/shorts/{id}")
    @RequirePermission("sys:video:upload")
    public Result<Void> deleteShort(@PathVariable Long id, HttpServletRequest request) {
        contentAppService.delete(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 删除本人视频。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @DeleteMapping("/user/videos/{id}")
    @RequirePermission("sys:video:upload")
    public Result<Void> deleteVideo(@PathVariable Long id, HttpServletRequest request) {
        contentAppService.delete(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 发布本人短视频。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/user/shorts/{id}/publish")
    @RequirePermission("sys:video:upload")
    public Result<Void> publishShort(@PathVariable Long id, HttpServletRequest request) {
        contentAppService.publishByOwner(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 下架本人短视频。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/user/shorts/{id}/offline")
    @RequirePermission("sys:video:upload")
    public Result<Void> offlineShort(@PathVariable Long id, HttpServletRequest request) {
        contentAppService.offlineByOwner(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 发布本人视频。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/user/videos/{id}/publish")
    @RequirePermission("sys:video:upload")
    public Result<Void> publishVideo(@PathVariable Long id, HttpServletRequest request) {
        contentAppService.publishByOwner(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 下架本人视频。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空结果
     */
    @PostMapping("/user/videos/{id}/offline")
    @RequirePermission("sys:video:upload")
    public Result<Void> offlineVideo(@PathVariable Long id, HttpServletRequest request) {
        contentAppService.offlineByOwner(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 我的短视频分页（含私密作品）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧分页
     */
    @GetMapping("/my/shorts")
    @RequirePermission("sys:video:upload")
    public Result<PageResult<OwnerContentView>> myShorts(
            @ModelAttribute ContentQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        applyPage(command, current, pageNum, size, pageSize);
        return Result.ok(contentAppService.myContent(command, TYPE_SHORT_VIDEO, UserContext.requireUserId(request)));
    }

    /**
     * 我的视频分页（含私密作品）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧分页
     */
    @GetMapping("/my/videos")
    @RequirePermission("sys:video:upload")
    public Result<PageResult<OwnerContentView>> myVideos(
            @ModelAttribute ContentQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        applyPage(command, current, pageNum, size, pageSize);
        return Result.ok(contentAppService.myContent(command, TYPE_VIDEO, UserContext.requireUserId(request)));
    }

    /**
     * 读取本人短视频编辑视图（含内部文件引用，仅上传者可见）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 运营视图（供编辑回填）
     */
    @GetMapping("/user/shorts/{id}")
    @RequirePermission("sys:video:upload")
    public Result<ContentView> ownerShort(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.ownerView(id, UserContext.requireUserId(request)));
    }

    /**
     * 读取本人视频编辑视图（含内部文件引用，仅上传者可见）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 运营视图（供编辑回填）
     */
    @GetMapping("/user/videos/{id}")
    @RequirePermission("sys:video:upload")
    public Result<ContentView> ownerVideo(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.ownerView(id, UserContext.requireUserId(request)));
    }

    /**
     * 内容详情（不可见内容按不存在处理）。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 详情视图
     */
    @GetMapping("/content/{id}")
    @RequirePermission("sys:video:browse")
    public Result<ContentDetailView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.detail(id, UserContext.optionalUserId(request)));
    }

    /**
     * 视频分页（仅已发布且可见内容）。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧分页
     */
    @GetMapping("/videos/page")
    @RequirePermission("sys:video:browse")
    public Result<PageResult<PublicContentView>> videoPage(
            @ModelAttribute ContentQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        applyPage(command, current, pageNum, size, pageSize);
        command.setContentType(TYPE_VIDEO);
        return Result.ok(contentAppService.publicPage(command, UserContext.optionalUserId(request)));
    }

    /**
     * 短视频 feed（键集分页：最新 / 热门 / 分类，返回下一页游标）。
     *
     * @param sort 排序：LATEST / HOT，非法值回落最新
     * @param categoryId 分类 ID，可选
     * @param cursor 游标，空白表示首屏
     * @param size 每批条数，可选（默认 10，上限 50）
     * @param request 当前请求（读取网关注入的用户头）
     * @return feed 视图
     */
    @GetMapping("/shorts/feed")
    @RequirePermission("sys:video:browse")
    public Result<FeedView> shortFeed(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Long size,
            HttpServletRequest request) {
        long batchSize = size == null ? 0L : size;
        return Result.ok(discoverAppService.shortFeed(sort, categoryId, cursor, batchSize,
                UserContext.optionalUserId(request)));
    }

    /**
     * 短视频详情。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 详情视图
     */
    @GetMapping("/shorts/{id}")
    @RequirePermission("sys:video:browse")
    public Result<ContentDetailView> shortDetail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.detail(id, UserContext.optionalUserId(request)));
    }

    /**
     * 视频详情。
     *
     * @param id 内容 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 详情视图
     */
    @GetMapping("/videos/{id}")
    @RequirePermission("sys:video:browse")
    public Result<ContentDetailView> videoDetail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(contentAppService.detail(id, UserContext.optionalUserId(request)));
    }

    /**
     * 归一化查询命令的分页参数。
     *
     * @param command 查询命令
     * @param current 页码（后端风格）
     * @param pageNum 页码（前端风格）
     * @param size 每页条数（后端风格）
     * @param pageSize 每页条数（前端风格）
     */
    private void applyPage(ContentQueryCommand command, Long current, Long pageNum, Long size, Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
    }
}
