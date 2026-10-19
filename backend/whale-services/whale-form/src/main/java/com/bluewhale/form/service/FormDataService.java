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
     * 提交表单数据：校验表单定义存在，落库一条状态为「有效」的数据
     *
     * @param request 提交请求：definitionId / dataJson 必填，bizKey 可选
     * @param submitter 提交人账号，可为 {@code null}
     * @return 表单数据视图对象
     */
    FormDataVO submit(FormDataSubmitRequest request, String submitter);

    /**
     * 表单数据详情
     *
     * @param id 表单数据ID
     * @return 表单数据视图对象
     */
    FormDataVO getDetail(Long id);

    /**
     * 分页查询表单数据
     *
     * @param request 查询条件
     * @return 表单数据分页结果
     */
    PageResult<FormDataVO> page(FormDataQueryRequest request);

    /**
     * 删除表单数据（逻辑删除）
     *
     * @param id 表单数据ID
     */
    void delete(Long id);
}
