package com.bluewhale.form.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDefinitionCopyRequest;
import com.bluewhale.form.dto.FormDefinitionSaveRequest;
import com.bluewhale.form.dto.FormQueryRequest;
import com.bluewhale.form.vo.FormDefinitionVO;
import com.bluewhale.form.vo.FormDefinitionVersionVO;

import java.util.List;

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

    /**
     * 复制表单定义（含字段配置与选项）
     *
     * @param id 源表单定义ID
     * @param request 复制请求（可空，编码/名称由服务端生成）
     * @return 新表单定义视图对象
     */
    FormDefinitionVO copy(Long id, FormDefinitionCopyRequest request);

    /**
     * 查询表单定义的版本快照列表（按版本号降序）
     *
     * @param id 表单定义ID
     * @return 版本快照视图对象列表
     */
    List<FormDefinitionVersionVO> listVersions(Long id);

    /**
     * 回滚表单定义到指定版本（回滚本身产生新版本）
     *
     * @param id 表单定义ID
     * @param version 目标版本号
     * @return 回滚后的表单定义视图对象
     */
    FormDefinitionVO rollback(Long id, Integer version);
}
