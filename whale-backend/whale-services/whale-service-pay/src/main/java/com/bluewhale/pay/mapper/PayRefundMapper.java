package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.po.PayRefundPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退款单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PayRefundMapper extends BaseMapper<PayRefundPo> {
}
