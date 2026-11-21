package com.bluewhale.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.alert.po.AlertNotifyLogPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警通知发送日志 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface AlertNotifyLogMapper extends BaseMapper<AlertNotifyLogPo> {
}
