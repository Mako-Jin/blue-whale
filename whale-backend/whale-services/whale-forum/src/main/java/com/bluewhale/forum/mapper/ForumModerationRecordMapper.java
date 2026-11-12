package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumModerationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 处置/审核轨迹 Mapper（append-only）。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumModerationRecordMapper extends BaseMapper<ForumModerationRecord> {
}
