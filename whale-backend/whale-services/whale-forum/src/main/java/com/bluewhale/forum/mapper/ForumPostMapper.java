package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 楼层 Mapper。
 *
 * <p>楼层号并发保护依赖唯一键 {@code uk_fm_post_floor}：插入撞键即 {@code DuplicateKeyException}，
 * 由 service 层重试，不做 {@code MAX(floor)+1} 预计算。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumPostMapper extends BaseMapper<ForumPost> {
}
