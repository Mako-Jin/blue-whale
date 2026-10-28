package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.entity.PayTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付/退款流水 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface PayTransactionMapper extends BaseMapper<PayTransaction> {
}
