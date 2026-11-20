package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.importexport.ExportResult;
import com.bluewhale.auth.application.model.importexport.ImportCommitResult;
import com.bluewhale.auth.application.model.importexport.ImportPreviewResult;
import com.bluewhale.auth.application.model.importexport.ImportRowError;
import com.bluewhale.auth.application.model.importexport.UserExportRow;
import com.bluewhale.auth.application.model.importexport.UserImportRow;
import com.bluewhale.auth.application.port.ExportFileUploader;
import com.bluewhale.auth.application.port.ImportBatchStore;
import com.bluewhale.auth.application.port.SpreadsheetReader;
import com.bluewhale.auth.application.port.SpreadsheetWriter;
import com.bluewhale.auth.application.service.PasswordPolicyService;
import com.bluewhale.auth.application.service.TenantMembershipAppService;
import com.bluewhale.auth.application.service.UserImportExportAppService;
import com.bluewhale.auth.domain.aggregate.UserAggregate;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.model.vo.EncryptedPassword;
import com.bluewhale.auth.domain.model.vo.Username;
import com.bluewhale.auth.domain.repository.UserRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户导入导出应用服务实现（权限中心扩展 M4）。
 *
 * <p><b>导入</b>：预检全量校验（不落库）→ 通过行暂存 Redis（30 分钟）→ 提交按每 500 行
 * 一个独立事务分批写入；事务内 {@code existsByUsername} 跳过同名用户计「已存在」；
 * 统一临时口令一次加密整批复用，导入用户强制首登改密、登记 {@code register_source=IMPORT}。</p>
 *
 * <p><b>导出</b>：流式分页写出 xlsx；≤5 万行同步返回字节流，超过同步生成后上传 whale-file
 * 返回 {@code objectKey}（实施偏差：暂无异步任务基建，不走异步）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportExportAppServiceImpl implements UserImportExportAppService {

    /** 导入数据行数上限（验收：1 万行） */
    static final int MAX_IMPORT_ROWS = 10_000;

    /** 同步导出行数上限，超过上传 whale-file */
    static final int MAX_EXPORT_ROWS = 50_000;

    /** 提交分批大小（每批一个独立事务） */
    static final int COMMIT_BATCH_SIZE = 500;

    /** 导出分页取数页大小 */
    static final int EXPORT_PAGE_SIZE = 1_000;

    /** 预检批次暂存有效期 */
    static final java.time.Duration BATCH_TTL = java.time.Duration.ofMinutes(30);

    /** 导入用户注册来源 */
    static final String REGISTER_SOURCE_IMPORT = "IMPORT";

    /** 导出文件名时间戳格式 */
    private static final DateTimeFormatter EXPORT_FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SpreadsheetReader spreadsheetReader;
    private final SpreadsheetWriter spreadsheetWriter;
    private final ImportBatchStore importBatchStore;
    private final ExportFileUploader exportFileUploader;
    private final UserRepository userRepository;
    private final UserImportRowValidator rowValidator;
    private final PasswordPolicyService passwordPolicyService;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final TenantMembershipAppService tenantMembershipAppService;
    private final DomainEventPublisher domainEventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    public byte[] template() {
        return spreadsheetWriter.writeTemplate();
    }

    @Override
    public ImportPreviewResult preview(InputStream input) {
        Long tenantId = TenantContext.requireId();
        List<UserImportRow> rows = spreadsheetReader.readUsers(input, MAX_IMPORT_ROWS);
        List<ImportRowError> errors = rowValidator.validate(rows);
        if (!errors.isEmpty()) {
            // 存在任意行级错误：不暂存批次，全量错误返回（验收：定位到行/列）
            return new ImportPreviewResult(null, rows.size(), 0, errors);
        }
        String batchId = UUID.randomUUID().toString().replace("-", "");
        importBatchStore.save(batchId, tenantId, rows, BATCH_TTL);
        return new ImportPreviewResult(batchId, rows.size(), rows.size(), List.of());
    }

    @Override
    public ImportCommitResult commit(String batchId) {
        Long tenantId = TenantContext.requireId();
        ImportBatchStore.StoredBatch batch = importBatchStore.load(batchId, tenantId);
        if (batch == null || batch.rows().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.import.precheck-expired",
                    "导入预检不存在或已过期，请重新上传预检");
        }
        importBatchStore.delete(batchId);
        List<UserImportRow> rows = batch.rows();
        // 统一临时口令：整批一次生成、一次加密复用（导入用户强制首登改密）
        String tempPassword = temporaryPasswordGenerator.generate(passwordPolicyService.minLength());
        String encoded = passwordPolicyService.encode(tempPassword);

        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (int start = 0; start < rows.size(); start += COMMIT_BATCH_SIZE) {
            List<UserImportRow> chunk = rows.subList(start, Math.min(start + COMMIT_BATCH_SIZE, rows.size()));
            // 每 500 行一个独立事务：单批失败不拖垮整批；跨批互不影响
            int[] counters = transactionTemplate.execute(status -> commitChunk(chunk, tenantId, encoded));
            if (counters != null) {
                success += counters[0];
                skipped += counters[1];
                failed += counters[2];
            }
        }
        log.info("用户导入完成: tenantId={}, total={}, success={}, skipped={}, failed={}",
                tenantId, rows.size(), success, skipped, failed);
        return new ImportCommitResult(rows.size(), success, skipped, failed, tempPassword);
    }

    /**
     * 提交一批（≤500 行）：同名跳过计「已存在」，行级失败隔离不拖垮整批
     *
     * @param chunk    本批导入行
     * @param tenantId 租户 ID
     * @param encoded  统一临时口令密文
     * @return [成功数, 跳过数, 失败数]
     */
    private int[] commitChunk(List<UserImportRow> chunk, Long tenantId, String encoded) {
        int success = 0;
        int skipped = 0;
        int failed = 0;
        for (UserImportRow row : chunk) {
            try {
                if (userRepository.existsByUsername(tenantId, row.username().trim())) {
                    skipped++;
                    continue;
                }
                UserAggregate user = UserAggregate.create(tenantId, Username.of(row.username().trim()),
                        EncryptedPassword.of(encoded), trimOrNull(row.nickname()), trimOrNull(row.phone()),
                        trimOrNull(row.email()), null, parseLongOrNull(row.deptId()),
                        parseStatus(row.status()), REGISTER_SOURCE_IMPORT);
                user.markMustChangePassword();
                UserAggregate persisted = userRepository.save(user);
                tenantMembershipAppService.ensureHomeMembership(persisted.getId(), tenantId);
                passwordPolicyService.recordHistory(tenantId, persisted.getId(), encoded);
                List<Long> roleIds = parseRoleIds(row.roleIds());
                if (!roleIds.isEmpty()) {
                    userRepository.replaceRoles(persisted.getId(), roleIds);
                }
                domainEventPublisher.publish(new UserDomainEvent(persisted.getId(), ChangeType.CREATED));
                success++;
            } catch (Exception e) {
                // 行级失败（并发唯一键冲突等）：隔离本行，不回滚整批
                failed++;
                log.warn("用户导入第 {} 行失败: {}", row.rowNo(), e.getMessage());
            }
        }
        return new int[]{success, skipped, failed};
    }

    @Override
    public ExportResult export(String keyword) {
        long total = userRepository.page(1, 1, keyword).total();
        if (total == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "auth.import.no-export-data", "没有可导出的用户数据");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long written = spreadsheetWriter.writeUsers(output, (pageIndex, pageSize) -> {
            List<UserAggregate> records = userRepository.page(pageIndex + 1, pageSize, keyword).records();
            // 当页角色 ID 一次批量查询，避免逐用户 N+1
            Map<Long, List<Long>> roleMap = userRepository.listRoleIdsByUserIds(
                    records.stream().map(UserAggregate::getId).toList());
            return records.stream().map(user -> toExportRow(user,
                    roleMap.getOrDefault(user.getId(), List.of()))).toList();
        });
        byte[] content = output.toByteArray();
        if (total > MAX_EXPORT_ROWS) {
            // 实施偏差：无异步任务基建，超限导出仍同步生成，转上传 whale-file 返回 objectKey
            String fileName = "users-" + LocalDateTime.now().format(EXPORT_FILE_TIME) + ".xlsx";
            Map<String, Object> remoteFile = exportFileUploader.upload(content, fileName);
            return new ExportResult(null, remoteFile, written);
        }
        return new ExportResult(content, null, written);
    }

    /**
     * 聚合快照 → 导出行（角色 ID 由调用方批量查询传入）
     */
    private UserExportRow toExportRow(UserAggregate user, List<Long> roleIds) {
        String joined = roleIds.isEmpty() ? null : joinIds(roleIds);
        return new UserExportRow(user.getUsername().value(), user.getNickname(), user.getPhone(), user.getEmail(),
                user.getDeptId(), joined, user.getStatus());
    }

    /**
     * 角色 ID 列表逗号拼接
     */
    private static String joinIds(List<Long> ids) {
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    /**
     * 解析部门 ID（校验器已保证格式；此处兜底容忍）
     */
    private static Long parseLongOrNull(String raw) {
        String value = trimOrNull(raw);
        return value == null ? null : Long.parseLong(value);
    }

    /**
     * 解析状态：空按启用处理（校验器已保证仅 0/1）
     */
    private static Integer parseStatus(String raw) {
        String value = trimOrNull(raw);
        return value == null ? null : Integer.parseInt(value);
    }

    /**
     * 解析角色 ID 单元格（校验器已保证格式）
     */
    private static List<Long> parseRoleIds(String raw) {
        String value = trimOrNull(raw);
        if (value == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                result.add(Long.parseLong(item));
            }
        }
        return result;
    }

    /**
     * 去空白；全空白视为空返回 {@code null}
     */
    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
