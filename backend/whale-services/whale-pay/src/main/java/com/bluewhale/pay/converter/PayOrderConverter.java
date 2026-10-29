package com.bluewhale.pay.converter;

import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.entity.PayTransaction;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayTransactionVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 支付模块实体/DTO 与 VO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段
 * 对齐，改名/删字段时编译报错而非静默丢数据；映射结果与原反射语义一致
 * （同名字段拷贝，其余字段由调用方显式设置）。</p>
 * @author BlueWhale
 */
@Mapper
public interface PayOrderConverter {

    /** 单例（无状态映射器，避免引入 Spring 依赖便于单测直接构造服务） */
    PayOrderConverter INSTANCE = Mappers.getMapper(PayOrderConverter.class);

    /**
     * 创建请求转支付单实体（orderNo/currency/channel/status 等由服务层归一化后显式设置）
     *
     * @param request 创建请求
     * @return 支付单实体
     */
    PayOrder toEntity(PayOrderCreateRequest request);

    /**
     * 支付单实体转视图对象
     *
     * @param order 支付单实体
     * @return 支付单视图对象
     */
    PayOrderVO toOrderVo(PayOrder order);

    /**
     * 流水实体转视图对象
     *
     * @param transaction 流水实体
     * @return 流水视图对象
     */
    PayTransactionVO toTransactionVo(PayTransaction transaction);
}
