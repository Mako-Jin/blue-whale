package com.bluewhale.party.activity.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.party.activity.infrastructure.persistence.entity.ActivitySigninEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织生活签到 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ActivitySigninMapper extends BaseMapper<ActivitySigninEntity> {
}
