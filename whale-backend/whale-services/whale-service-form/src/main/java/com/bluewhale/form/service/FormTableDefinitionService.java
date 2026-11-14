package com.bluewhale.form.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.model.request.FormQueryRequest;
import com.bluewhale.form.model.request.FormTableDefinitionSaveRequest;
import com.bluewhale.form.model.vo.FormTableDefinitionVO;

/**
 * 表定义服务（表定义层：表 + 列聚合）。
 *
 * @author BlueWhale
 */
public interface FormTableDefinitionService {

    /**
     * 新增表定义（含列集合）
     *
     * @param request 表定义请求参数：tableCode / tableName 必填
     * @return 表定义视图对象（含列）
     */
    FormTableDefinitionVO create(FormTableDefinitionSaveRequest request);

    /**
     * 更新表定义（整体替换列集合）
     *
     * @param id 表定义ID
     * @param request 表定义请求参数
     * @return 表定义视图对象（含列）
     */
    FormTableDefinitionVO update(Long id, FormTableDefinitionSaveRequest request);

    /**
     * 删除表定义（逻辑删除表与其列）
     *
     * @param id 表定义ID
     */
    void delete(Long id);

    /**
     * 表定义详情（含列）
     *
     * @param id 表定义ID
     * @return 表定义视图对象（含列）
     */
    FormTableDefinitionVO getDetail(Long id);

    /**
     * 分页查询表定义
     *
     * @param request 查询条件：name 模糊（表名）、code 等值（表编码）、status 等值
     * @return 表定义分页结果
     */
    PageResult<FormTableDefinitionVO> page(FormQueryRequest request);
}
