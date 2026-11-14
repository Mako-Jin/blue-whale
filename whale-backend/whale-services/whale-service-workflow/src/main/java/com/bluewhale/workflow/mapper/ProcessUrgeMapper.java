package com.bluewhale.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.workflow.po.ProcessUrgePo;

/**
 * 流程催办 Mapper。
 *
 * <p>无需 {@code @Mapper}：由 MybatisPlusConfig 的 {@code @MapperScan} 统一扫描。
 * 频控依赖唯一键 {@code (tenant_id, instance_id, urger, window_bucket)}，
 * Service 层捕获 {@code DuplicateKeyException} 作为并发兜底。</p>
 *
 * @author BlueWhale
 */
public interface ProcessUrgeMapper extends BaseMapper<ProcessUrgePo> {
}
