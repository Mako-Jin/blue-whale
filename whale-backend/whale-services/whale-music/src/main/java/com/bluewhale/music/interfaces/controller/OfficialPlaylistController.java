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
 * 官方歌单接口：运营维护官方歌单。
 *
 * <p>官方歌单 {@code ownerId = 0}、类型 OFFICIAL、可见性固定 PUBLIC；
 * 全部接口要求 {@code sys:music:playlist:manage}，写操作记录审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/music/v{version}/playlist/official", version = "1")
@RequiredArgsConstructor
public class OfficialPlaylistController {

    private final PlaylistAppService playlistAppService;

    /**
     * 创建官方歌单。
     *
     * @param command 歌单保存命令
     * @return 歌单视图
     */
    @PostMapping
    @RequirePermission("sys:music:playlist:manage")
    @AuditLog(operation = "创建官方歌单", type = OperateType.CREATE, targetType = "mu_playlist")
    public Result<PlaylistView> create(@Valid @RequestBody PlaylistSaveCommand command) {
        return Result.ok(playlistAppService.createOfficial(command));
    }

    /**
     * 修改官方歌单。
     *
     * @param id 歌单 ID
     * @param command 歌单保存命令
     * @return 歌单视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:music:playlist:manage")
    @AuditLog(operation = "修改官方歌单", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<PlaylistView> update(@PathVariable Long id, @Valid @RequestBody PlaylistSaveCommand command) {
        return Result.ok(playlistAppService.updateOfficial(id, command));
    }

    /**
     * 逻辑删除官方歌单。
     *
     * @param id 歌单 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:music:playlist:manage")
    @AuditLog(operation = "删除官方歌单", type = OperateType.DELETE, targetType = "mu_playlist", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        playlistAppService.deleteOfficial(id);
        return Result.ok();
    }

    /**
     * 官方歌单分页。
     *
     * @param command 查询命令
     * @return 歌单分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:music:playlist:manage")
    public Result<PageResult<PlaylistView>> page(@ModelAttribute PlaylistQueryCommand command) {
        return Result.ok(playlistAppService.officialPage(command));
    }

    /**
     * 向官方歌单加歌（运营维护；重复加入幂等）。
     *
     * @param id 歌单 ID
     * @param command 加歌命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单详情
     */
    @PostMapping("/{id}/song")
    @RequirePermission("sys:music:playlist:manage")
    @AuditLog(operation = "官方歌单加歌", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<PlaylistView> addSong(@PathVariable Long id,
                                        @Valid @RequestBody AddPlaylistSongCommand command,
                                        HttpServletRequest request) {
        return Result.ok(playlistAppService.addSongAsOperator(id, command, UserContext.requireUserId(request)));
    }

    /**
     * 从官方歌单移除歌曲（运营维护，幂等）。
     *
     * @param id 歌单 ID
     * @param songId 歌曲 ID
     * @param request 当前请求（读取网关注入的用户头）
     * @return 空响应
     */
    @DeleteMapping("/{id}/song/{songId}")
    @RequirePermission("sys:music:playlist:manage")
    @AuditLog(operation = "官方歌单移除歌曲", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<Void> removeSong(@PathVariable Long id, @PathVariable Long songId,
                                   HttpServletRequest request) {
        playlistAppService.removeSongAsOperator(id, songId, UserContext.requireUserId(request));
        return Result.ok();
    }

    /**
     * 重排官方歌单歌曲（运营维护）。
     *
     * @param id 歌单 ID
     * @param command 重排命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌单详情
     */
    @PutMapping("/{id}/sort")
    @RequirePermission("sys:music:playlist:manage")
    @AuditLog(operation = "官方歌单重排", type = OperateType.UPDATE, targetType = "mu_playlist", targetId = "#id")
    public Result<PlaylistView> reorder(@PathVariable Long id,
                                        @Valid @RequestBody ReorderPlaylistCommand command,
                                        HttpServletRequest request) {
        return Result.ok(playlistAppService.reorderAsOperator(id, command, UserContext.requireUserId(request)));
    }
}
