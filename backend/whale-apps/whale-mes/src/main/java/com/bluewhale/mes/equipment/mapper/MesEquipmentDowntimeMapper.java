package com.bluewhale.mes.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mes.equipment.entity.MesEquipmentDowntime;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备停机记录 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface MesEquipmentDowntimeMapper extends BaseMapper<MesEquipmentDowntime> {
}
