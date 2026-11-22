package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.po.PayChannelCallbackLogPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 渠道回调日志 Mapper。
 *
 * <p>回调请求不信任入站租户头，日志表也不参与自动租户隔离，因此整个 Mapper
 * 通过 {@link InterceptorIgnore} 屏蔽租户插件；租户由支付单反查后显式写入。</p>
 *
 * @author BlueWhale
 */
@InterceptorIgnore(tenantLine = "true")
public interface PayChannelCallbackLogMapper extends BaseMapper<PayChannelCallbackLogPo> {

    /**
     * 按主键加行锁读取（并发回调串行化的关键）。
     *
     * @param id 日志 ID
     * @return 日志行；不存在返回 {@code null}
     */
    @Select("SELECT * FROM pay_channel_callback_log WHERE id = #{id} FOR UPDATE")
    PayChannelCallbackLogPo selectByIdForUpdate(@Param("id") Long id);
}
