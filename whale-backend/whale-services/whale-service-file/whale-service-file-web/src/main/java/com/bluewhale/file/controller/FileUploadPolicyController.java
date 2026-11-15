package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.api.FileUploadPolicyApi;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.request.UploadPolicyUpdateRequest;
import com.bluewhale.file.model.response.UploadPolicyResponse;
import com.bluewhale.file.service.support.UploadPolicyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 上传策略热更新接口实现（Web 适配层）。
 *
 * <p>GET 展示开关、全局默认上限与当前规则；PUT 整表替换规则并返回更新后的视图。
 * 开关与全局默认上限仍由启动配置决定，不通过本接口修改。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class FileUploadPolicyController implements FileUploadPolicyApi {

    /** 文件管理权限码：上传上限放宽属于运维操作，仅管理权限可读写 */
    private static final String PERM_FILE_MANAGE = "sys:file:manage";

    private final FileProperties fileProperties;

    private final UploadPolicyRegistry uploadPolicyRegistry;

    @Override
    @RequirePermission(PERM_FILE_MANAGE)
    public Result<UploadPolicyResponse> get() {
        return Result.ok(view(uploadPolicyRegistry.rules()));
    }

    @Override
    @RequirePermission(PERM_FILE_MANAGE)
    public Result<UploadPolicyResponse> update(UploadPolicyUpdateRequest request) {
        List<FileProperties.Rule> rules = request.getRules().stream()
                .map(rule -> FileProperties.Rule.of(rule.getPermission(), rule.getMaxSizeBytes(),
                        rule.getQuotaBytes()))
                .toList();
        return Result.ok(view(uploadPolicyRegistry.replace(rules)));
    }

    /**
     * 构造上传策略视图
     *
     * @param rules 当前生效规则
     * @return 上传策略视图
     */
    private UploadPolicyResponse view(List<FileProperties.Rule> rules) {
        UploadPolicyResponse response = new UploadPolicyResponse();
        response.setEnabled(fileProperties.getUploadPolicy().isEnabled());
        response.setDefaultMaxSizeBytes(fileProperties.getMaxSizeBytes());
        response.setDefaultQuotaBytes(fileProperties.getQuotaBytes());
        response.setRules(rules.stream().map(FileUploadPolicyController::toRule).toList());
        return response;
    }

    /**
     * 规则转响应条目
     *
     * @param rule 规则
     * @return 响应条目
     */
    private static UploadPolicyResponse.Rule toRule(FileProperties.Rule rule) {
        UploadPolicyResponse.Rule item = new UploadPolicyResponse.Rule();
        item.setPermission(rule.getPermission());
        item.setMaxSizeBytes(rule.getMaxSizeBytes());
        item.setQuotaBytes(rule.getQuotaBytes());
        return item;
    }
}
