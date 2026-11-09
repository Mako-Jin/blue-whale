package com.bluewhale.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.assistant.entity.AsstMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 助手消息 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface AsstMessageMapper extends BaseMapper<AsstMessage> {
}
