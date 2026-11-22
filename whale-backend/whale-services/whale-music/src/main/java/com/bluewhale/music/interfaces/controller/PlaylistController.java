package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.music.application.command.AddPlaylistSongCommand;
import com.bluewhale.music.application.command.PlaylistQueryCommand;
import com.bluewhale.music.application.command.PlaylistSaveCommand;
import com.bluewhale.music.application.command.ReorderPlaylistCommand;
import com.bluewhale.music.application.service.PlaylistAppService;
import com.bluewhale.music.application.view.PlaylistView;
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
 * 用户歌单接口：歌单维护、加歌 / 移除 / 重排与浏览。
 *
 * <p>所有接口要求 {@code sys:music:playlist:own}，写操作校验歌单归属并记录审计；
 * 官方歌单用户侧只读，跨用户私有歌单按不存在处理。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/music/v{version}/playlist", version = "1")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistAppService playlistAppService;

    /**
     * 创建用户歌单，所有者固定为当前用户。
     *
     * @param command 歌单保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单视图
     */
    @PostMapping
    @RequirePermission("sys:music:playlist:own")
    @AuditLog(operation = "创建歌单", type = OperateType.CREATE, targetType = "mu_playlist")
    public Result<PlaylistView> create(@Valid @RequestBody PlaylistSaveCommand command,
                                       HttpServletRequest request) {
        return Result.ok(playlistAppService.create(command, UserContext.requireUserId(request)));
    }

    /**
     * 修改自己的歌单。
     *
     * @param id 歌单 ID
     * @param command 歌单保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:music:playlist:own")
    @AuditLog(operation = "修改歌单", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<PlaylistView> update(@PathVariable Long id, @Valid @RequestBody PlaylistSaveCommand command,
                                       HttpServletRequest request) {
        return Result.ok(playlistAppService.update(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 逻辑删除自己的歌单。
     *
     * @param id 歌单 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:music:playlist:own")
    @AuditLog(operation = "删除歌单", type = OperateType.DELETE, targetType = "mu_playlist", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        playlistAppService.delete(id, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 歌单分页：公开歌单（含官方）与本人私有歌单。
     *
     * @param command 查询命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:music:playlist:own")
    public Result<PageResult<PlaylistView>> page(@ModelAttribute PlaylistQueryCommand command,
                                                 HttpServletRequest request) {
        return Result.ok(playlistAppService.page(command, UserContext.requireUserId(request)));
    }

    /**
     * 歌单详情（含歌曲与排序号）。
     *
     * @param id 歌单 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:music:playlist:own")
    public Result<PlaylistView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(playlistAppService.detail(id, UserContext.requireUserId(request)));
    }

    /**
     * 向歌单加歌（仅所有者；重复加入幂等）。
     *
     * @param id 歌单 ID
     * @param command 加歌命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单详情
     */
    @PostMapping("/{id}/song")
    @RequirePermission("sys:music:playlist:own")
    @AuditLog(operation = "歌单加歌", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<PlaylistView> addSong(@PathVariable Long id, @Valid @RequestBody AddPlaylistSongCommand command,
                                        HttpServletRequest request) {
        return Result.ok(playlistAppService.addSong(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 从歌单移除歌曲（幂等）。
     *
     * @param id 歌单 ID
     * @param songId 歌曲 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空响应
     */
    @DeleteMapping("/{id}/song/{songId}")
    @RequirePermission("sys:music:playlist:own")
    @AuditLog(operation = "歌单移除歌曲", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<Void> removeSong(@PathVariable Long id, @PathVariable Long songId,
                                   HttpServletRequest request) {
        playlistAppService.removeSong(id, songId, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 重排歌单内歌曲。
     *
     * @param id 歌单 ID
     * @param command 重排命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单详情
     */
    @PutMapping("/{id}/sort")
    @RequirePermission("sys:music:playlist:own")
    @AuditLog(operation = "歌单重排", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<PlaylistView> reorder(@PathVariable Long id, @Valid @RequestBody ReorderPlaylistCommand command,
                                        HttpServletRequest request) {
        return Result.ok(playlistAppService.reorder(id, command, UserContext.requireUserId(request)));
    }
}
