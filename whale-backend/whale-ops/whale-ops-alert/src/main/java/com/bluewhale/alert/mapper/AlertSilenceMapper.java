package com.bluewhale.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.alert.po.AlertSilencePo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警静默规则 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface AlertSilenceMapper extends BaseMapper<AlertSilencePo> {
}
