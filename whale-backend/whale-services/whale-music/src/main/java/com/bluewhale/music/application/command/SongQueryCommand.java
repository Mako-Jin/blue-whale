package com.bluewhale.music.application.command;

import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.SongVisibility;
import lombok.Data;

/**
 * 歌曲分页查询命令。
 *
 * @author BlueWhale
 */
@Data
public class SongQueryCommand {

    /** 关键词（标题 / 副标题模糊匹配） */
    private String keyword;

    /** 歌手 ID */
    private Long artistId;

    /** 专辑 ID */
    private Long albumId;

    /** 标签 ID */
    private Long tagId;

    /** 发布状态 */
    private PublishState publishState;

    /** 可见性 */
    private SongVisibility visibility;

    /** 上传者 ID（我的作品；由服务端强制填充，前端传入无效） */
    private Long uploaderId;

    /** 排序：NEW 上架时间倒序 / HOT 播放次数倒序 */
    private String sortBy;

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数，最大 200 */
    private long size = 20;
}
