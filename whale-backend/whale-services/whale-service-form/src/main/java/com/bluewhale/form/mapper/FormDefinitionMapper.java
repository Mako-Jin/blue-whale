package com.bluewhale.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.stat.FormDefinitionStatPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 表单定义 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface FormDefinitionMapper extends BaseMapper<FormDefinitionPo> {

    /**
     * 表单定义总数与启用数（考功殿概览 T5.7）
     *
     * <p>禁用（{@code status = 0}）的定义仍然占着编码与物理表，只是不再对外提交；
     * 屏风上必须能看出"12 个表单里只有 9 个还开着"，否则停用过的表单会一直算在考核基数里。</p>
     *
     * @param tenantId 租户 ID
     * @return 统计；无数据时两列均为 0
     */
    @Select("SELECT COUNT(*) AS definitions, "
            + "COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS enabled "
            + "FROM form_definition WHERE tenant_id = #{tenantId} AND deleted = 0")
    FormDefinitionStatPo countDefinitions(@Param("tenantId") Long tenantId);
}
