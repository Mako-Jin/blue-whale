package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.music.application.command.SongQueryCommand;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.service.UserSongAppService;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.application.view.SongView;
import com.bluewhale.music.interfaces.support.UserContext;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户上传与作品接口：我的作品维护、用户侧公开浏览。
 *
 * <p>上传 / 编辑 / 删除要求 {@code sys:music:upload} 并校验作品归属；
 * 公开浏览要求 {@code sys:music:browse}，仅返回上架且公开的作品。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class UserSongController {

    private final UserSongAppService userSongAppService;

    /**
     * 用户上传歌曲（需已签署当前版本版权协议，创建即发布）。
     *
     * @param command 歌曲保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧歌曲视图
     */
    @PostMapping("/user/song")
    @RequirePermission("sys:music:upload")
    @AuditLog(operation = "上传歌曲", type = OperateType.CREATE, targetType = "mu_song", recordParams = false)
    public Result<PublicSongView> upload(@Valid @RequestBody SongSaveCommand command, HttpServletRequest request) {
        return Result.ok(userSongAppService.upload(command, UserContext.requireUserId(request)));
    }

    /**
     * 用户编辑自己的作品。
     *
     * @param id 歌曲 ID
     * @param command 歌曲保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧歌曲视图
     */
    @PutMapping("/user/song/{id}")
    @RequirePermission("sys:music:upload")
    @AuditLog(operation = "编辑我的作品", type = OperateType.UPDATE, targetType = "mu_song",
            targetId = "#id", recordParams = false)
    public Result<PublicSongView> update(@PathVariable Long id, @Valid @RequestBody SongSaveCommand command,
                                         HttpServletRequest request) {
        return Result.ok(userSongAppService.update(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 用户删除自己的作品。
     *
     * @param id 歌曲 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空响应
     */
    @DeleteMapping("/user/song/{id}")
    @RequirePermission("sys:music:upload")
    @AuditLog(operation = "删除我的作品", type = OperateType.DELETE, targetType = "mu_song", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        userSongAppService.delete(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 我的作品（含自听作品，供编辑回填）。
     *
     * @param command 查询命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌曲分页结果
     */
    @GetMapping("/my/songs")
    @RequirePermission("sys:music:upload")
    public Result<PageResult<SongView>> mySongs(@ModelAttribute SongQueryCommand command,
                                               HttpServletRequest request) {
        return Result.ok(userSongAppService.mySongs(command, UserContext.requireUserId(request)));
    }

    /**
     * 用户侧公开歌曲分页。
     *
     * @param command 查询命令
     * @return 用户侧歌曲分页结果
     */
    @GetMapping("/public/song/page")
    @RequirePermission("sys:music:browse")
    public Result<PageResult<PublicSongView>> publicPage(@ModelAttribute SongQueryCommand command) {
        return Result.ok(userSongAppService.publicPage(command));
    }

    /**
     * 用户侧歌曲详情，私有 / 下架 / 跨租户一律按不存在处理。
     *
     * @param id 歌曲 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 用户侧歌曲视图
     */
    @GetMapping("/public/song/{id}")
    @RequirePermission("sys:music:browse")
    public Result<PublicSongView> publicDetail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(userSongAppService.publicDetail(id, UserContext.requireUserId(request)));
    }
}
