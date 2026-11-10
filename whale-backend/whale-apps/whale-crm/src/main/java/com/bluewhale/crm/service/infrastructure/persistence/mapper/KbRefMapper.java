package com.bluewhale.crm.service.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.service.infrastructure.persistence.entity.KbRefEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识引用 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface KbRefMapper extends BaseMapper<KbRefEntity> {
}
