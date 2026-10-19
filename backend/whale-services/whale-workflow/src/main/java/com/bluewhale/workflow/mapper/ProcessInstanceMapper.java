package com.bluewhale.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.workflow.entity.ProcessInstance;

/**
 * 流程实例 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface ProcessInstanceMapper extends BaseMapper<ProcessInstance> {
}
