package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysLoginHistoryPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录历史 Mapper（append-only，仅 INSERT/SELECT）。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysLoginHistoryMapper extends BaseMapper<SysLoginHistoryPo> {
}
