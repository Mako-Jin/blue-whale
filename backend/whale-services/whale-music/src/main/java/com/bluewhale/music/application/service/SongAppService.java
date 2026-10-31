package com.bluewhale.music.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.music.application.command.SongQueryCommand;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.view.SongView;

/**
 * 歌曲运营应用服务：曲库维护、发布状态机与运营分页。
 *
 * <p>用户上传入口由 {@code UserSongAppService} 承担，二者共用歌曲聚合与状态机。</p>
 *
 * @author BlueWhale
 */
public interface SongAppService {

    /**
     * 运营新增歌曲（默认草稿）。
     *
     * @param command 歌曲保存命令
     * @param operatorId 操作用户 ID（记录为上传者）
     * @return 歌曲视图
     */
    SongView create(SongSaveCommand command, Long operatorId);

    /**
     * 运营更新歌曲。
     *
     * @param id 歌曲 ID
     * @param command 歌曲保存命令
     * @return 歌曲视图
     */
    SongView update(Long id, SongSaveCommand command);

    /**
     * 逻辑删除歌曲。
     *
     * @param id 歌曲 ID
     */
    void delete(Long id);

    /**
     * 运营侧歌曲详情。
     *
     * @param id 歌曲 ID
     * @return 歌曲视图
     */
    SongView detail(Long id);

    /**
     * 运营侧歌曲分页。
     *
     * @param command 查询命令
     * @return 歌曲分页结果
     */
    PageResult<SongView> page(SongQueryCommand command);

    /**
     * 上架歌曲。
     *
     * @param id 歌曲 ID
     * @return 歌曲视图
     */
    SongView publish(Long id);

    /**
     * 下架歌曲。
     *
     * @param id 歌曲 ID
     * @return 歌曲视图
     */
    SongView offline(Long id);

    /**
     * 版权下架歌曲。
     *
     * @param id 歌曲 ID
     * @param reason 版权下架原因
     * @return 歌曲视图
     */
    SongView takedown(Long id, String reason);
}
