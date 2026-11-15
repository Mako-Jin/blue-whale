package com.bluewhale.file.api;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.request.UploadPolicyUpdateRequest;
import com.bluewhale.file.model.response.UploadPolicyResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 上传策略热更新 API 契约。
 *
 * <p>只热更新「按权限码的规则集合」，开关与全局默认上限仍由启动配置决定；
 * 两个接口都要求 {@code sys:file:manage}，避免普通用户自行放宽上传上限。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/upload-policy", version = "1")
public interface FileUploadPolicyApi {

    /**
     * 查询当前生效的上传策略视图。
     *
     * @return 开关、全局默认上限与当前规则集合
     */
    @GetMapping
    Result<UploadPolicyResponse> get();

    /**
     * 整表替换按权限码的上传规则，并返回更新后的视图。
     *
     * <p>规则只保存在当前实例内存，多副本部署时需分别更新；重启后回到配置文件中的规则。</p>
     *
     * @param request 新规则集合，不能为空
     * @return 更新后的上传策略视图
     */
    @PutMapping
    Result<UploadPolicyResponse> update(@Valid @RequestBody UploadPolicyUpdateRequest request);
}
