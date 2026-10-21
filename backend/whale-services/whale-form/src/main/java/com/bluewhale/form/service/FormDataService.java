package com.bluewhale.form.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.form.dto.FormDataQueryRequest;
import com.bluewhale.form.dto.FormDataSubmitRequest;
import com.bluewhale.form.dto.FormDataUpdateRequest;
import com.bluewhale.form.vo.FormDataExportVO;
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
     * 修改表单数据（审批环节改表单）：合并提交字段后双写主表与物理表
     *
     * @param id 表单数据ID
     * @param request 修改请求：{@code dataJson} 必填
     * @param tenantId 租户ID（行级隔离）
     * @return 表单数据视图对象（含结构化 {@code fields}）
     */
    FormDataVO update(Long id, FormDataUpdateRequest request, Long tenantId);

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

    /**
     * 导出表单数据为 CSV / XLSX 字节流
     *
     * <p>列顺序 = 公共列（ID/表单编码/版本/业务键/提交人/提交时间/状态）+ 表单视图的数据字段
     * （取自 {@code schema_json} 快照的 {@code fields[]}，键为 {@code columnCode}）；行取主表
     * {@code form_data} 并解析 {@code dataJson} 还原各列值，单次查询无 N+1。</p>
     *
     * @param request 查询条件：{@code definitionCode} 必填，{@code bizKey} / {@code submitter} 可选
     * @param tenantId 租户ID
     * @param format 导出格式：{@code csv} 或 {@code xlsx}
     * @return 导出产物（内容 + 文件名 + Content-Type）
     */
    FormDataExportVO export(FormDataQueryRequest request, Long tenantId, String format);
}
