package com.bluewhale.form.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDefinitionSaveRequest;
import com.bluewhale.form.dto.FormQueryRequest;
import com.bluewhale.form.vo.FormDefinitionVO;

/**
 * 表单定义服务
 *
 * @author BlueWhale
 */
public interface FormDefinitionService {

    /**
     * 新增表单定义
     *
     * @param request 表单定义请求参数
     * @return 表单定义视图对象
     */
    FormDefinitionVO create(FormDefinitionSaveRequest request);

    /**
     * 更新表单定义
     *
     * @param id 表单定义ID
     * @param request 表单定义请求参数
     * @return 表单定义视图对象
     */
    FormDefinitionVO update(Long id, FormDefinitionSaveRequest request);

    /**
     * 删除表单定义（逻辑删除）
     *
     * @param id 表单定义ID
     */
    void delete(Long id);

    /**
     * 表单定义详情
     *
     * @param id 表单定义ID
     * @return 表单定义视图对象
     */
    FormDefinitionVO getDetail(Long id);

    /**
     * 分页查询表单定义
     *
     * @param request 查询条件
     * @return 表单定义分页结果
     */
    PageResult<FormDefinitionVO> page(FormQueryRequest request);
}
