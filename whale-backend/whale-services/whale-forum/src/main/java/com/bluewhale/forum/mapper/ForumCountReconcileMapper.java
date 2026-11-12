package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumCountReconcile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计数对账轨迹 Mapper（append-only，只有插入与查询）。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumCountReconcileMapper extends BaseMapper<ForumCountReconcile> {
}
