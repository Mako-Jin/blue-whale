package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumThreadEditHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子编辑历史 Mapper（append-only，仅插入与查询）。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumThreadEditHistoryMapper extends BaseMapper<ForumThreadEditHistory> {
}
