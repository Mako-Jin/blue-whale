package com.bluewhale.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.form.po.FormReconcileDiffPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 表单主从数据对账差异 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface FormReconcileDiffMapper extends BaseMapper<FormReconcileDiffPo> {

    /**
     * 待处理的差异条数（考功殿概览 T5.7）
     *
     * <p>只数 {@code status = 0}：人工确认"已修复"或"已忽略"的不再占着这一格——
     * 差异表是只增不改的登记簿，累计条数会只涨不跌，看起来像永远处理不完。</p>
     *
     * <p>跨批次累计（不限最近一次对账）：某一批里登记过、之后一直没人处理的差异，
     * 正是最该被看见的那几条。</p>
     *
     * @param tenantId 租户 ID
     * @return 待处理条数
     */
    @Select("SELECT COUNT(*) FROM form_reconcile_diff "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 0")
    long countPending(@Param("tenantId") Long tenantId);
}
