package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumSensitiveHit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词命中留痕 Mapper（append-only）。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumSensitiveHitMapper extends BaseMapper<ForumSensitiveHit> {
}
