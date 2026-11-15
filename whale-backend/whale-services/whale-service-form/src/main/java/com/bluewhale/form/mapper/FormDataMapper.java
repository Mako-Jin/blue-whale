package com.bluewhale.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.form.po.FormDataPo;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 表单数据 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface FormDataMapper extends BaseMapper<FormDataPo> {

    /**
     * 查询存在未逻辑删除表单数据的租户ID（去重）
     *
     * <p>供对账任务遍历租户使用：任务启动时租户上下文为空，租户插件不会追加租户条件。</p>
     *
     * @return 活跃租户ID列表
     */
    @Select("SELECT DISTINCT tenant_id FROM form_data WHERE deleted = 0")
    List<Long> selectActiveTenantIds();
}
