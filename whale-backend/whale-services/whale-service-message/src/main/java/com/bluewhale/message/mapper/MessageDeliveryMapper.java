package com.bluewhale.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.message.po.MessageDeliveryPo;

/**
 * 投递记录 Mapper。
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface MessageDeliveryMapper extends BaseMapper<MessageDeliveryPo> {
}
