package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.AlbumSaveCommand;
import com.bluewhale.music.application.command.ArtistSaveCommand;
import com.bluewhale.music.application.command.TagSaveCommand;
import com.bluewhale.music.application.view.AlbumView;
import com.bluewhale.music.application.view.ArtistView;
import com.bluewhale.music.application.view.TagView;

import java.util.List;

/**
 * 曲库运营应用服务：歌手、专辑、标签维护。
 *
 * @author BlueWhale
 */
public interface CatalogAppService {

    /**
     * 新增歌手。
     *
     * @param command 歌手保存命令
     * @return 歌手视图
     */
    ArtistView createArtist(ArtistSaveCommand command);

    /**
     * 更新歌手。
     *
     * @param id 歌手 ID
     * @param command 歌手保存命令
     * @return 歌手视图
     */
    ArtistView updateArtist(Long id, ArtistSaveCommand command);

    /**
     * 逻辑删除歌手。
     *
     * @param id 歌手 ID
     */
    void deleteArtist(Long id);

    /**
     * 歌手详情。
     *
     * @param id 歌手 ID
     * @return 歌手视图
     */
    ArtistView artistDetail(Long id);

    /**
     * 歌手分页。
     *
     * @param keyword 名称关键词，可为空
     * @param status 状态，可为空
     * @param current 页码
     * @param size 每页条数
     * @return 歌手分页结果
     */
    PageResult<ArtistView> artistPage(String keyword, Integer status, long current, long size);

    /**
     * 新增专辑。
     *
     * @param command 专辑保存命令
     * @return 专辑视图
     */
    AlbumView createAlbum(AlbumSaveCommand command);

    /**
     * 更新专辑。
     *
     * @param id 专辑 ID
     * @param command 专辑保存命令
     * @return 专辑视图
     */
    AlbumView updateAlbum(Long id, AlbumSaveCommand command);

    /**
     * 逻辑删除专辑。
     *
     * @param id 专辑 ID
     */
    void deleteAlbum(Long id);

    /**
     * 专辑详情。
     *
     * @param id 专辑 ID
     * @return 专辑视图
     */
    AlbumView albumDetail(Long id);

    /**
     * 专辑分页。
     *
     * @param keyword 标题关键词，可为空
     * @param artistId 歌手 ID，可为空
     * @param publishState 发布状态文本（DRAFT / PUBLISHED / OFFLINE），可为空
     * @param current 页码
     * @param size 每页条数
     * @return 专辑分页结果
     */
    PageResult<AlbumView> albumPage(String keyword, Long artistId, String publishState, long current, long size);

    /**
     * 上架专辑。
     *
     * @param id 专辑 ID
     * @return 专辑视图
     */
    AlbumView publishAlbum(Long id);

    /**
     * 下架专辑。
     *
     * @param id 专辑 ID
     * @return 专辑视图
     */
    AlbumView offlineAlbum(Long id);

    /**
     * 新增标签。
     *
     * @param command 标签保存命令
     * @return 标签视图
     */
    TagView createTag(TagSaveCommand command);

    /**
     * 更新标签。
     *
     * @param id 标签 ID
     * @param command 标签保存命令
     * @return 标签视图
     */
    TagView updateTag(Long id, TagSaveCommand command);

    /**
     * 逻辑删除标签。
     *
     * @param id 标签 ID
     */
    void deleteTag(Long id);

    /**
     * 查询全部标签。
     *
     * @return 标签列表
     */
    List<TagView> listTags();
}
