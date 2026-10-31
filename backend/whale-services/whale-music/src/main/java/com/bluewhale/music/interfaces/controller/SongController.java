package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.music.application.command.SongQueryCommand;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.command.TakedownCommand;
import com.bluewhale.music.application.service.SongAppService;
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
 * 歌曲运营接口：曲库维护、发布 / 下架 / 版权下架与运营查询。
 *
 * <p>写操作全部要求歌曲运营权限码并记录审计；歌词正文不写入审计参数。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class SongController {

    private final SongAppService songAppService;

    /**
     * 运营新增歌曲（默认草稿，需显式发布）。
     *
     * @param command 歌曲保存命令
     * @param request 当前请求（读取网关注入的用户头）
     * @return 歌曲视图
     */
    @PostMapping("/song")
    @RequirePermission("sys:music:song:add")
    @AuditLog(operation = "新增歌曲", type = OperateType.CREATE, targetType = "mu_song", recordParams = false)
    public Result<SongView> create(@Valid @RequestBody SongSaveCommand command, HttpServletRequest request) {
        return Result.ok(songAppService.create(command, UserContext.requireUserId(request)));
    }

    /**
     * 运营更新歌曲。
     *
     * @param id 歌曲 ID
     * @param command 歌曲保存命令
     * @return 歌曲视图
     */
    @PutMapping("/song/{id}")
    @RequirePermission("sys:music:song:edit")
    @AuditLog(operation = "更新歌曲", type = OperateType.UPDATE, targetType = "mu_song",
            targetId = "#id", recordParams = false)
    public Result<SongView> update(@PathVariable Long id, @Valid @RequestBody SongSaveCommand command) {
        return Result.ok(songAppService.update(id, command));
    }

    /**
     * 逻辑删除歌曲。
     *
     * @param id 歌曲 ID
     * @return 空响应
     */
    @DeleteMapping("/song/{id}")
    @RequirePermission("sys:music:song:delete")
    @AuditLog(operation = "删除歌曲", type = OperateType.DELETE, targetType = "mu_song", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        songAppService.delete(id);
        return Result.ok();
    }

    /**
     * 上架歌曲。
     *
     * @param id 歌曲 ID
     * @return 歌曲视图
     */
    @PostMapping("/song/{id}/publish")
    @RequirePermission("sys:music:song:publish")
    @AuditLog(operation = "上架歌曲", type = OperateType.UPDATE, targetType = "mu_song", targetId = "#id")
    public Result<SongView> publish(@PathVariable Long id) {
        return Result.ok(songAppService.publish(id));
    }

    /**
     * 下架歌曲。
     *
     * @param id 歌曲 ID
     * @return 歌曲视图
     */
    @PostMapping("/song/{id}/offline")
    @RequirePermission("sys:music:song:publish")
    @AuditLog(operation = "下架歌曲", type = OperateType.UPDATE, targetType = "mu_song", targetId = "#id")
    public Result<SongView> offline(@PathVariable Long id) {
        return Result.ok(songAppService.offline(id));
    }

    /**
     * 版权下架歌曲：强制下线并拒绝播放。
     *
     * @param id 歌曲 ID
     * @param command 版权下架命令
     * @return 歌曲视图
     */
    @PostMapping("/song/{id}/takedown")
    @RequirePermission("sys:music:song:publish")
    @AuditLog(operation = "版权下架歌曲", type = OperateType.UPDATE, targetType = "mu_song", targetId = "#id")
    public Result<SongView> takedown(@PathVariable Long id, @Valid @RequestBody TakedownCommand command) {
        return Result.ok(songAppService.takedown(id, command.getReason()));
    }

    /**
     * 运营歌曲分页（含草稿与自听作品）。
     *
     * @param command 查询命令
     * @return 歌曲分页结果
     */
    @GetMapping("/song/page")
    @RequirePermission("sys:music:song:list")
    public Result<PageResult<SongView>> page(@ModelAttribute SongQueryCommand command) {
        return Result.ok(songAppService.page(command));
    }

    /**
     * 运营歌曲详情。
     *
     * @param id 歌曲 ID
     * @return 歌曲视图
     */
    @GetMapping("/song/{id}")
    @RequirePermission("sys:music:song:list")
    public Result<SongView> detail(@PathVariable Long id) {
        return Result.ok(songAppService.detail(id));
    }
}
