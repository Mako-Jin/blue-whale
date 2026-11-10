package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.SongQueryCommand;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.view.PublicSongView;
import com.bluewhale.music.application.view.SongView;

/**
 * 用户上传与作品管理应用服务。
 *
 * <p>所有登录用户均可上传：上传前须签署当前版本《版权无争议协议》，
 * 创建即发布，并可选公开（PUBLIC）或自听（PRIVATE）。自听作品仅上传者可见，
 * 公开作品租户内可见，运营事后复核并可下架。</p>
 *
 * @author BlueWhale
 */
public interface UserSongAppService {

    /**
     * 用户上传歌曲（签署校验通过后创建并立即上架）。
     *
     * @param command 歌曲保存命令
     * @param userId 上传用户 ID
     * @return 用户侧歌曲视图
     */
    PublicSongView upload(SongSaveCommand command, Long userId);

    /**
     * 用户编辑自己的作品。
     *
     * @param id 歌曲 ID
     * @param command 歌曲保存命令
     * @param userId 操作用户 ID
     * @return 用户侧歌曲视图
     */
    PublicSongView update(Long id, SongSaveCommand command, Long userId);

    /**
     * 用户删除自己的作品。
     *
     * @param id 歌曲 ID
     * @param userId 操作用户 ID
     */
    void delete(Long id, Long userId);

    /**
     * 我的作品（含自听作品，运营侧视图用于编辑回填）。
     *
     * @param command 查询命令
     * @param userId 当前用户 ID
     * @return 歌曲分页结果
     */
    PageResult<SongView> mySongs(SongQueryCommand command, Long userId);

    /**
     * 用户侧公开歌曲分页（仅上架且公开的作品）。
     *
     * @param command 查询命令
     * @return 用户侧歌曲分页结果
     */
    PageResult<PublicSongView> publicPage(SongQueryCommand command);

    /**
     * 用户侧歌曲详情，不可见时按不存在处理。
     *
     * @param id 歌曲 ID
     * @param viewerId 查看者用户 ID
     * @return 用户侧歌曲视图
     */
    PublicSongView publicDetail(Long id, Long viewerId);
}
