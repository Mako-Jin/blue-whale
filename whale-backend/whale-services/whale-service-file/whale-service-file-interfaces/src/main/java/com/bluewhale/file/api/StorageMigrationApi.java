package com.bluewhale.file.api;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.request.StorageMigrationRequest;
import com.bluewhale.file.model.response.StorageMigrationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 存储迁移 API 契约：把指定源介质的文件迁移到当前活动插件。
 *
 * <p>管理类写接口，要求 {@code sys:file:manage}；非 dry-run 需显式 {@code confirm=true}。
 * 每次仅处理有界批次，可重复调用直至返回的扫描数为 0。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}/storage-migrations", version = "1")
public interface StorageMigrationApi {

    /**
     * 触发一批存储迁移。
     *
     * @param request 迁移参数
     * @return 迁移汇总
     */
    @PostMapping
    Result<StorageMigrationResponse> migrate(@Valid @RequestBody StorageMigrationRequest request);
}
