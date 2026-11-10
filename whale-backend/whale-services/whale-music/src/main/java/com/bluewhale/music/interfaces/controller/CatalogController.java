package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.music.application.command.AlbumSaveCommand;
import com.bluewhale.music.application.command.ArtistSaveCommand;
import com.bluewhale.music.application.command.TagSaveCommand;
import com.bluewhale.music.application.service.CatalogAppService;
import com.bluewhale.music.application.view.AlbumView;
import com.bluewhale.music.application.view.ArtistView;
import com.bluewhale.music.application.view.TagView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 曲库运营接口：歌手、专辑、标签维护。
 *
 * <p>服务内路径前缀 {@code /music}；写操作全部要求运营权限码并记录审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/music")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogAppService catalogAppService;

    /**
     * 新增歌手。
     *
     * @param command 歌手保存命令
     * @return 歌手视图
     */
    @PostMapping("/artist")
    @RequirePermission("sys:music:artist:add")
    @AuditLog(operation = "新增歌手", type = OperateType.CREATE, targetType = "mu_artist")
    public Result<ArtistView> createArtist(@Valid @RequestBody ArtistSaveCommand command) {
        return Result.ok(catalogAppService.createArtist(command));
    }

    /**
     * 更新歌手。
     *
     * @param id 歌手 ID
     * @param command 歌手保存命令
     * @return 歌手视图
     */
    @PutMapping("/artist/{id}")
    @RequirePermission("sys:music:artist:edit")
    @AuditLog(operation = "更新歌手", type = OperateType.UPDATE, targetType = "mu_artist", targetId = "#id")
    public Result<ArtistView> updateArtist(@PathVariable Long id, @Valid @RequestBody ArtistSaveCommand command) {
        return Result.ok(catalogAppService.updateArtist(id, command));
    }

    /**
     * 逻辑删除歌手。
     *
     * @param id 歌手 ID
     * @return 空响应
     */
    @DeleteMapping("/artist/{id}")
    @RequirePermission("sys:music:artist:delete")
    @AuditLog(operation = "删除歌手", type = OperateType.DELETE, targetType = "mu_artist", targetId = "#id")
    public Result<Void> deleteArtist(@PathVariable Long id) {
        catalogAppService.deleteArtist(id);
        return Result.ok();
    }

    /**
     * 歌手分页。
     *
     * @param keyword 名称关键词，可为空
     * @param status 状态，可为空
     * @param current 页码
     * @param size 每页条数
     * @return 歌手分页结果
     */
    @GetMapping("/artist/page")
    @RequirePermission("sys:music:artist:list")
    public Result<PageResult<ArtistView>> artistPage(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size) {
        return Result.ok(catalogAppService.artistPage(keyword, status, current, size));
    }

    /**
     * 歌手详情。
     *
     * @param id 歌手 ID
     * @return 歌手视图
     */
    @GetMapping("/artist/{id}")
    @RequirePermission("sys:music:artist:list")
    public Result<ArtistView> artistDetail(@PathVariable Long id) {
        return Result.ok(catalogAppService.artistDetail(id));
    }

    /**
     * 新增专辑。
     *
     * @param command 专辑保存命令
     * @return 专辑视图
     */
    @PostMapping("/album")
    @RequirePermission("sys:music:album:add")
    @AuditLog(operation = "新增专辑", type = OperateType.CREATE, targetType = "mu_album")
    public Result<AlbumView> createAlbum(@Valid @RequestBody AlbumSaveCommand command) {
        return Result.ok(catalogAppService.createAlbum(command));
    }

    /**
     * 更新专辑。
     *
     * @param id 专辑 ID
     * @param command 专辑保存命令
     * @return 专辑视图
     */
    @PutMapping("/album/{id}")
    @RequirePermission("sys:music:album:edit")
    @AuditLog(operation = "更新专辑", type = OperateType.UPDATE, targetType = "mu_album", targetId = "#id")
    public Result<AlbumView> updateAlbum(@PathVariable Long id, @Valid @RequestBody AlbumSaveCommand command) {
        return Result.ok(catalogAppService.updateAlbum(id, command));
    }

    /**
     * 逻辑删除专辑。
     *
     * @param id 专辑 ID
     * @return 空响应
     */
    @DeleteMapping("/album/{id}")
    @RequirePermission("sys:music:album:delete")
    @AuditLog(operation = "删除专辑", type = OperateType.DELETE, targetType = "mu_album", targetId = "#id")
    public Result<Void> deleteAlbum(@PathVariable Long id) {
        catalogAppService.deleteAlbum(id);
        return Result.ok();
    }

    /**
     * 专辑分页。
     *
     * @param keyword 标题关键词，可为空
     * @param artistId 歌手 ID，可为空
     * @param publishState 发布状态文本，可为空
     * @param current 页码
     * @param size 每页条数
     * @return 专辑分页结果
     */
    @GetMapping("/album/page")
    @RequirePermission("sys:music:album:list")
    public Result<PageResult<AlbumView>> albumPage(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Long artistId,
                                                   @RequestParam(required = false) String publishState,
                                                   @RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "20") long size) {
        return Result.ok(catalogAppService.albumPage(keyword, artistId, publishState, current, size));
    }

    /**
     * 专辑详情。
     *
     * @param id 专辑 ID
     * @return 专辑视图
     */
    @GetMapping("/album/{id}")
    @RequirePermission("sys:music:album:list")
    public Result<AlbumView> albumDetail(@PathVariable Long id) {
        return Result.ok(catalogAppService.albumDetail(id));
    }

    /**
     * 上架专辑。
     *
     * @param id 专辑 ID
     * @return 专辑视图
     */
    @PostMapping("/album/{id}/publish")
    @RequirePermission("sys:music:album:publish")
    @AuditLog(operation = "上架专辑", type = OperateType.UPDATE, targetType = "mu_album", targetId = "#id")
    public Result<AlbumView> publishAlbum(@PathVariable Long id) {
        return Result.ok(catalogAppService.publishAlbum(id));
    }

    /**
     * 下架专辑。
     *
     * @param id 专辑 ID
     * @return 专辑视图
     */
    @PostMapping("/album/{id}/offline")
    @RequirePermission("sys:music:album:publish")
    @AuditLog(operation = "下架专辑", type = OperateType.UPDATE, targetType = "mu_album", targetId = "#id")
    public Result<AlbumView> offlineAlbum(@PathVariable Long id) {
        return Result.ok(catalogAppService.offlineAlbum(id));
    }

    /**
     * 新增标签。
     *
     * @param command 标签保存命令
     * @return 标签视图
     */
    @PostMapping("/tag")
    @RequirePermission("sys:music:tag:manage")
    @AuditLog(operation = "新增标签", type = OperateType.CREATE, targetType = "mu_tag")
    public Result<TagView> createTag(@Valid @RequestBody TagSaveCommand command) {
        return Result.ok(catalogAppService.createTag(command));
    }

    /**
     * 更新标签。
     *
     * @param id 标签 ID
     * @param command 标签保存命令
     * @return 标签视图
     */
    @PutMapping("/tag/{id}")
    @RequirePermission("sys:music:tag:manage")
    @AuditLog(operation = "更新标签", type = OperateType.UPDATE, targetType = "mu_tag", targetId = "#id")
    public Result<TagView> updateTag(@PathVariable Long id, @Valid @RequestBody TagSaveCommand command) {
        return Result.ok(catalogAppService.updateTag(id, command));
    }

    /**
     * 逻辑删除标签。
     *
     * @param id 标签 ID
     * @return 空响应
     */
    @DeleteMapping("/tag/{id}")
    @RequirePermission("sys:music:tag:manage")
    @AuditLog(operation = "删除标签", type = OperateType.DELETE, targetType = "mu_tag", targetId = "#id")
    public Result<Void> deleteTag(@PathVariable Long id) {
        catalogAppService.deleteTag(id);
        return Result.ok();
    }

    /**
     * 查询全部标签（用户侧筛选也复用该接口）。
     *
     * @return 标签列表
     */
    @GetMapping("/tag/list")
    @RequirePermission("sys:music:browse")
    public Result<List<TagView>> listTags() {
        return Result.ok(catalogAppService.listTags());
    }
}
