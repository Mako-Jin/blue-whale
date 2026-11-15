package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.api.StorageMigrationApi;
import com.bluewhale.file.assemble.StorageMigrationAssembler;
import com.bluewhale.file.model.command.StorageMigrationCommand;
import com.bluewhale.file.model.request.StorageMigrationRequest;
import com.bluewhale.file.model.response.StorageMigrationResponse;
import com.bluewhale.file.service.StorageMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存储迁移接口实现（Web 适配层）。
 *
 * <p>管理类写接口：要求 {@code sys:file:manage}；非 dry-run 需显式确认，避免误触大批量迁移。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class StorageMigrationController implements StorageMigrationApi {

    private final StorageMigrationService storageMigrationService;

    private final StorageMigrationAssembler storageMigrationAssembler;

    @Override
    @RequirePermission("sys:file:manage")
    public Result<StorageMigrationResponse> migrate(StorageMigrationRequest request) {
        StorageMigrationCommand command = new StorageMigrationCommand(request.getSourceStorageType(),
                request.getBucket(), request.getLimit(), request.isDryRun(), request.isDeleteSource(),
                request.isConfirm());
        return Result.ok(storageMigrationAssembler.toResponse(storageMigrationService.migrate(command)));
    }
}
