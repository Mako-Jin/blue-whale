package com.bluewhale.music.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.music.infrastructure.persistence.entity.SongCommentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌曲评论 Mapper，对应表 {@code mu_song_comment}。
 *
 * @author BlueWhale
 */
@Mapper
public interface SongCommentMapper extends BaseMapper<SongCommentEntity> {
}
