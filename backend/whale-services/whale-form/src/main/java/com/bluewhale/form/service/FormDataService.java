package com.bluewhale.form.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
import com.bluewhale.form.vo.FormDataVO;

/**
 * 表单数据服务
 *
 * @author BlueWhale
 */
public interface FormDataService {

    /**
     * 提交表单数据：校验表单定义存在且当前版本已发布，按 schema 校验字段后双写主表与物理表
     *
     * @param request 提交请求：definitionId / dataJson 必填，bizKey 可选
     * @param submitter 提交人账号，可为 {@code null}
     * @param tenantId 租户ID（行级隔离）
     * @return 表单数据视图对象（含结构化 {@code fields}）
     */
    FormDataVO submit(FormDataSubmitRequest request, String submitter, Long tenantId);

    /**
     * 表单数据详情：主记录 + 按其版本从物理表读取的结构化字段
     *
     * @param id 表单数据ID
     * @param tenantId 租户ID
     * @return 表单数据视图对象（含结构化 {@code fields}）
     */
    FormDataVO getDetail(Long id, Long tenantId);

    /**
     * 分页查询表单数据（按租户过滤）
     *
     * @param request 查询条件
     * @param tenantId 租户ID
     * @return 表单数据分页结果
     */
    PageResult<FormDataVO> page(FormDataQueryRequest request, Long tenantId);

    /**
     * 删除表单数据（主表与物理表逻辑删除）
     *
     * @param id 表单数据ID
     * @param tenantId 租户ID
     */
    void delete(Long id, Long tenantId);
}
