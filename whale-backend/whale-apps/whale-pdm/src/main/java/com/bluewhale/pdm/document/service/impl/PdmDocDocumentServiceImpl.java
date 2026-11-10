package com.bluewhale.pdm.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.converter.PdmConverter;
import com.bluewhale.pdm.document.client.PdmDocFileClient;
import com.bluewhale.pdm.document.dto.PdmDocDocumentQueryRequest;
import com.bluewhale.pdm.document.dto.PdmDocDocumentSaveRequest;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.document.service.PdmDocDocumentService;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.support.PdmDocAclConstants;
import com.bluewhale.pdm.document.support.PdmDocAclDecision;
import com.bluewhale.pdm.document.support.PdmDocCsvWriter;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import com.bluewhale.pdm.document.support.PdmDocExport;
import com.bluewhale.pdm.document.support.PdmDocUploadFile;
import com.bluewhale.pdm.document.support.PdmDocVersionNo;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocDocumentVO;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图文档服务实现
 *
 * <p>版本规则：首个草稿 {@code V0.1}；发布转为 {@code V1.0}；
 * 后续上传/检入生成 {@code V1.1}、{@code V1.2}；回滚以历史版本为基线新建草稿。
 * 已发布版本不可修改，删除文档仅允许无正式版本的情况。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmDocDocumentServiceImpl implements PdmDocDocumentService {

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 10L;

    /** 最大每页条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 导出最大行数 */
    private static final int EXPORT_MAX_ROWS = 5000;

    /** 导出分批查询每页条数 */
    private static final long EXPORT_PAGE_SIZE = 200L;

    /** 文档状态：草稿 */
    private static final String STATUS_DRAFT = "DRAFT";

    /** 文档状态：已发布 */
    private static final String STATUS_RELEASED = "RELEASED";

    /** 版本状态：草稿 */
    private static final String VERSION_DRAFT = "DRAFT";

    /** 版本状态：已发布 */
    private static final String VERSION_RELEASED = "RELEASED";

    /** 版本状态：已作废 */
    private static final String VERSION_OBSOLETE = "OBSOLETE";

    /** 上传来源：普通上传 */
    private static final String SOURCE_UPLOAD = "UPLOAD";

    /** 上传来源：检入 */
    private static final String SOURCE_CHECKIN = "CHECKIN";

    /** 上传来源：回滚 */
    private static final String SOURCE_ROLLBACK = "ROLLBACK";

    private final PdmDocDocumentMapper documentMapper;

    private final PdmDocVersionMapper versionMapper;

    private final PdmDocFolderService folderService;

    private final PdmDocFileClient fileClient;

    private final PdmDocAclResolver aclResolver;

    @Override
    public PageResult<PdmDocDocumentVO> page(PdmDocDocumentQueryRequest request, PdmDocViewer viewer) {
        long current = request.getCurrent() <= 0 ? 1 : request.getCurrent();
        long size = request.getSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getSize(), MAX_PAGE_SIZE);
        LambdaQueryWrapper<PdmDocDocument> wrapper = buildWrapper(request, viewer);
        Page<PdmDocDocument> page = new Page<>(current, size);
        Page<PdmDocDocument> result = documentMapper.selectPage(page, wrapper);
        List<PdmDocDocumentVO> records = result.getRecords().stream()
                .map(PdmConverter.INSTANCE::toDocumentVo)
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PdmDocExport export(PdmDocDocumentQueryRequest request, PdmDocViewer viewer) {
        LambdaQueryWrapper<PdmDocDocument> wrapper = buildWrapper(request, viewer);
        List<PdmDocDocument> rows = new ArrayList<>(EXPORT_MAX_ROWS);
        long total = 0L;
        long current = 1L;
        while (rows.size() < EXPORT_MAX_ROWS) {
            long size = Math.min(EXPORT_PAGE_SIZE, EXPORT_MAX_ROWS - rows.size());
            Page<PdmDocDocument> result = documentMapper.selectPage(new Page<>(current, size), wrapper);
            total = result.getTotal();
            rows.addAll(result.getRecords());
            if (result.getRecords().size() < size) {
                break;
            }
            current++;
        }
        return new PdmDocExport(PdmDocCsvWriter.toCsvBytes(rows), total > EXPORT_MAX_ROWS);
    }

    /**
     * 构建文档查询条件（分页与导出共用，含 ACL DENY 过滤）
     *
     * @param request 查询条件
     * @param viewer  访问者
     * @return 查询条件
     */
    private LambdaQueryWrapper<PdmDocDocument> buildWrapper(PdmDocDocumentQueryRequest request, PdmDocViewer viewer) {
        LambdaQueryWrapper<PdmDocDocument> wrapper = new LambdaQueryWrapper<>();
        if (request.getFolderId() != null) {
            List<Long> folderIds = folderService.subtreeFolderIds(request.getFolderId());
            wrapper.in(PdmDocDocument::getFolderId, folderIds);
        }
        if (StringUtils.hasText(request.getDocType())) {
            wrapper.eq(PdmDocDocument::getDocType, request.getDocType());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(PdmDocDocument::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getPartNo())) {
            wrapper.like(PdmDocDocument::getPartNo, request.getPartNo().trim());
        }
        if (request.getOwnerId() != null) {
            wrapper.eq(PdmDocDocument::getOwnerId, request.getOwnerId());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(query -> query.like(PdmDocDocument::getDocNo, keyword)
                    .or().like(PdmDocDocument::getTitle, keyword)
                    .or().like(PdmDocDocument::getPartNo, keyword)
                    .or().like(PdmDocDocument::getTags, keyword)
                    .or().like(PdmDocDocument::getDescription, keyword)
                    .or().like(PdmDocDocument::getOwnerName, keyword));
        }
        if (request.getBeginDate() != null) {
            wrapper.ge(PdmDocDocument::getCreateTime, request.getBeginDate().atStartOfDay());
        }
        if (request.getEndDate() != null) {
            wrapper.lt(PdmDocDocument::getCreateTime, request.getEndDate().plusDays(1).atStartOfDay());
        }
        List<Long> deniedFolders = aclResolver.deniedFolderIds(viewer);
        if (!deniedFolders.isEmpty()) {
            wrapper.notIn(PdmDocDocument::getFolderId, deniedFolders);
        }
        List<Long> deniedDocuments = aclResolver.deniedDocumentIds(viewer);
        if (!deniedDocuments.isEmpty()) {
            wrapper.notIn(PdmDocDocument::getId, deniedDocuments);
        }
        wrapper.orderByDesc(PdmDocDocument::getUpdateTime).orderByDesc(PdmDocDocument::getId);
        return wrapper;
    }

    @Override
    public PdmDocDocumentVO detail(Long id, PdmDocViewer viewer) {
        PdmDocDocument document = requireDocument(id);
        assertReadAccess(document, viewer, PdmDocAclConstants.ACTION_VIEW);
        return PdmConverter.INSTANCE.toDocumentVo(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocDocumentVO create(PdmDocDocumentSaveRequest request, Long userId, String userName) {
        folderService.get(request.getFolderId());
        String docNo = StringUtils.hasText(request.getDocNo()) ? request.getDocNo().trim() : generateDocNo();
        assertDocNoUnique(docNo, null);
        PdmDocDocument document = PdmConverter.INSTANCE.toDocumentEntity(request);
        document.setDocNo(docNo);
        document.setStatus(STATUS_DRAFT);
        document.setOwnerId(userId == null ? 0L : userId);
        document.setOwnerName(userName);
        documentMapper.insert(document);
        return PdmConverter.INSTANCE.toDocumentVo(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocDocumentVO update(Long id, PdmDocDocumentSaveRequest request, PdmDocViewer viewer,
                                   boolean canManage) {
        PdmDocDocument document = requireDocument(id);
        assertWriteAccess(document, viewer, PdmDocAclConstants.ACTION_EDIT, canManage);
        folderService.get(request.getFolderId());
        if (StringUtils.hasText(request.getDocNo())) {
            assertDocNoUnique(request.getDocNo().trim(), id);
            document.setDocNo(request.getDocNo().trim());
        }
        document.setFolderId(request.getFolderId());
        document.setTitle(request.getTitle().trim());
        document.setDocType(request.getDocType());
        document.setPartNo(request.getPartNo());
        document.setDescription(request.getDescription());
        document.setTags(request.getTags());
        documentMapper.updateById(document);
        return PdmConverter.INSTANCE.toDocumentVo(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, PdmDocViewer viewer, boolean canManage) {
        PdmDocDocument document = requireDocument(id);
        assertWriteAccess(document, viewer, PdmDocAclConstants.ACTION_DELETE, canManage);
        long releasedCount = versionMapper.selectCount(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, id)
                .eq(PdmDocVersion::getStatus, VERSION_RELEASED));
        if (releasedCount > 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "文档存在已发布版本，不能删除");
        }
        versionMapper.delete(new LambdaQueryWrapper<PdmDocVersion>().eq(PdmDocVersion::getDocumentId, id));
        documentMapper.deleteById(id);
    }

    @Override
    public List<PdmDocVersionVO> versions(Long documentId, PdmDocViewer viewer) {
        PdmDocDocument document = requireDocument(documentId);
        assertReadAccess(document, viewer, PdmDocAclConstants.ACTION_VIEW);
        return versionMapper.selectList(new LambdaQueryWrapper<PdmDocVersion>()
                        .eq(PdmDocVersion::getDocumentId, documentId)
                        .orderByDesc(PdmDocVersion::getVersionSeq))
                .stream()
                .map(PdmConverter.INSTANCE::toVersionVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocVersionVO uploadVersion(Long documentId, PdmDocUploadFile file, String changeNote,
                                         PdmDocViewer viewer, boolean canManage) {
        PdmDocDocument document = requireDocument(documentId);
        assertWriteAccess(document, viewer, PdmDocAclConstants.ACTION_EDIT, canManage);
        assertNotCheckedOutByOther(document, viewer == null ? null : viewer.userId());
        PdmDocVersion version = createDraft(document, file, changeNote,
                viewer == null ? null : viewer.userId(), SOURCE_UPLOAD);
        documentMapper.updateById(document);
        return PdmConverter.INSTANCE.toVersionVo(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocDocumentVO checkOut(Long documentId, PdmDocViewer viewer) {
        PdmDocDocument document = requireDocument(documentId);
        assertWriteAccess(document, viewer, PdmDocAclConstants.ACTION_EDIT, false);
        Long userId = viewer == null ? null : viewer.userId();
        if (document.getCheckedOutBy() != null) {
            if (document.getCheckedOutBy().equals(userId)) {
                return PdmConverter.INSTANCE.toDocumentVo(document);
            }
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "文档已被他人检出");
        }
        document.setCheckedOutBy(userId == null ? 0L : userId);
        document.setCheckedOutTime(LocalDateTime.now());
        documentMapper.updateById(document);
        return PdmConverter.INSTANCE.toDocumentVo(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocVersionVO checkIn(Long documentId, PdmDocUploadFile file, String changeNote,
                                   PdmDocViewer viewer) {
        PdmDocDocument document = requireDocument(documentId);
        assertWriteAccess(document, viewer, PdmDocAclConstants.ACTION_EDIT, false);
        Long userId = viewer == null ? null : viewer.userId();
        if (document.getCheckedOutBy() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文档未检出，不能检入");
        }
        if (!document.getCheckedOutBy().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "文档由他人检出，不能检入");
        }
        PdmDocVersion version = createDraft(document, file, changeNote, userId, SOURCE_CHECKIN);
        document.setCheckedOutBy(null);
        document.setCheckedOutTime(null);
        documentMapper.updateById(document);
        return PdmConverter.INSTANCE.toVersionVo(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocVersionVO release(Long documentId, Long versionId, PdmDocViewer viewer, boolean canManage) {
        PdmDocDocument document = requireDocument(documentId);
        assertWriteAccess(document, viewer, PdmDocAclConstants.ACTION_EDIT, canManage);
        PdmDocVersion version = requireVersion(documentId, versionId);
        if (!VERSION_DRAFT.equals(version.getStatus())) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "只有草稿版本可以发布");
        }
        PdmDocVersionNo target = PdmDocVersionNo.parse(version.getVersionNo()).releaseTarget();
        if (!target.value().equals(version.getVersionNo())) {
            assertVersionNoUnique(documentId, target.value(), versionId);
            version.setVersionNo(target.value());
            version.setVersionSeq(target.seq());
        }
        version.setStatus(VERSION_RELEASED);
        version.setReleasedBy(viewer == null ? null : viewer.userId());
        version.setReleasedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        versionMapper.update(null, new LambdaUpdateWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, documentId)
                .eq(PdmDocVersion::getStatus, VERSION_RELEASED)
                .ne(PdmDocVersion::getId, versionId)
                .set(PdmDocVersion::getStatus, VERSION_OBSOLETE));
        document.setCurrentVersionId(version.getId());
        document.setLatestVersionId(version.getId());
        document.setLatestVersionNo(version.getVersionNo());
        document.setStatus(STATUS_RELEASED);
        documentMapper.updateById(document);
        return PdmConverter.INSTANCE.toVersionVo(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocVersionVO rollback(Long documentId, Long versionId, PdmDocViewer viewer, boolean canManage) {
        PdmDocDocument document = requireDocument(documentId);
        assertWriteAccess(document, viewer, PdmDocAclConstants.ACTION_EDIT, canManage);
        assertNotCheckedOutByOther(document, viewer == null ? null : viewer.userId());
        PdmDocVersion source = requireVersion(documentId, versionId);
        PdmDocVersion latest = latestVersion(documentId);
        PdmDocVersionNo next = latest == null
                ? PdmDocVersionNo.initialDraft()
                : PdmDocVersionNo.parse(latest.getVersionNo()).nextDraft();
        PdmDocVersion version = new PdmDocVersion();
        version.setDocumentId(documentId);
        version.setVersionNo(next.value());
        version.setVersionSeq(next.seq());
        version.setStatus(VERSION_DRAFT);
        version.setFileId(source.getFileId());
        version.setFileName(source.getFileName());
        version.setFileSize(source.getFileSize());
        version.setContentType(source.getContentType());
        version.setChecksum(source.getChecksum());
        version.setChangeNote("回滚自 " + source.getVersionNo());
        version.setSource(SOURCE_ROLLBACK);
        version.setCreatedBy(viewer == null ? null : viewer.userId());
        versionMapper.insert(version);
        document.setLatestVersionId(version.getId());
        document.setLatestVersionNo(version.getVersionNo());
        documentMapper.updateById(document);
        return PdmConverter.INSTANCE.toVersionVo(version);
    }

    @Override
    public PdmDocDownload download(Long documentId, Long versionId, PdmDocViewer viewer) {
        PdmDocDocument document = requireDocument(documentId);
        assertReadAccess(document, viewer, PdmDocAclConstants.ACTION_DOWNLOAD);
        PdmDocVersion version = requireVersion(documentId, versionId);
        if (version.getFileId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "版本文件不存在");
        }
        byte[] content = fileClient.content(version.getFileId());
        return new PdmDocDownload(content, version.getFileName(), version.getContentType());
    }

    @Override
    public PdmDocDownload contentOfReleasedVersion(Long documentId, String versionNo) {
        requireDocument(documentId);
        List<PdmDocVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, documentId)
                .eq(PdmDocVersion::getVersionNo, versionNo)
                .last("LIMIT 1"));
        if (versions.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档版本不存在");
        }
        PdmDocVersion version = versions.get(0);
        if (!VERSION_RELEASED.equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅已发布文档版本可内网读取");
        }
        if (version.getFileId() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "版本文件不存在");
        }
        byte[] content = fileClient.content(version.getFileId());
        return new PdmDocDownload(content, version.getFileName(), version.getContentType());
    }

    /**
     * 创建草稿版本并回填文档最新版本字段（不落库文档，交由调用方统一更新）
     *
     * @param document   文档
     * @param file       上传文件
     * @param changeNote 变更说明
     * @param userId     上传人
     * @param source     来源
     * @return 新版本
     */
    private PdmDocVersion createDraft(PdmDocDocument document, PdmDocUploadFile file,
                                      String changeNote, Long userId, String source) {
        validateFile(file);
        String fileName = safeFileName(file.fileName());
        String contentType = StringUtils.hasText(file.contentType()) ? file.contentType() : "application/octet-stream";
        PdmDocFileClient.UploadedFile uploaded = fileClient.upload(file.content(), fileName, contentType,
                userId, TenantContext.requireId());
        PdmDocVersion latest = latestVersion(document.getId());
        PdmDocVersionNo next = latest == null
                ? PdmDocVersionNo.initialDraft()
                : PdmDocVersionNo.parse(latest.getVersionNo()).nextDraft();
        PdmDocVersion version = new PdmDocVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(next.value());
        version.setVersionSeq(next.seq());
        version.setStatus(VERSION_DRAFT);
        version.setFileId(uploaded.fileId());
        version.setFileName(uploaded.originalName() == null ? fileName : uploaded.originalName());
        version.setFileSize(file.size());
        version.setContentType(contentType);
        version.setChecksum(sha256(file.content()));
        version.setChangeNote(changeNote);
        version.setSource(source);
        version.setCreatedBy(userId);
        versionMapper.insert(version);
        document.setLatestVersionId(version.getId());
        document.setLatestVersionNo(version.getVersionNo());
        return version;
    }

    /**
     * 校验上传文件
     *
     * @param file 上传文件
     */
    private void validateFile(PdmDocUploadFile file) {
        if (file == null || file.content() == null || file.content().length == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上传文件不能为空");
        }
        if (!StringUtils.hasText(file.fileName())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }
    }

    /**
     * 查询文档最新版本
     *
     * @param documentId 文档 ID
     * @return 最新版本，不存在返回 {@code null}
     */
    private PdmDocVersion latestVersion(Long documentId) {
        List<PdmDocVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, documentId)
                .orderByDesc(PdmDocVersion::getVersionSeq)
                .last("LIMIT 1"));
        return versions.isEmpty() ? null : versions.get(0);
    }

    /**
     * 查询并校验文档存在
     *
     * @param id 文档 ID
     * @return 文档实体
     */
    private PdmDocDocument requireDocument(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        PdmDocDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    /**
     * 查询并校验版本属于文档
     *
     * @param documentId 文档 ID
     * @param versionId  版本 ID
     * @return 版本实体
     */
    private PdmDocVersion requireVersion(Long documentId, Long versionId) {
        PdmDocVersion version = versionMapper.selectById(versionId);
        if (version == null || !version.getDocumentId().equals(documentId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档版本不存在");
        }
        return version;
    }

    /**
     * 校验文档编号唯一
     *
     * @param docNo   文档编号
     * @param exclude 排除的文档 ID
     */
    private void assertDocNoUnique(String docNo, Long exclude) {
        LambdaQueryWrapper<PdmDocDocument> wrapper = new LambdaQueryWrapper<PdmDocDocument>()
                .eq(PdmDocDocument::getDocNo, docNo);
        if (exclude != null) {
            wrapper.ne(PdmDocDocument::getId, exclude);
        }
        if (documentMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "文档编号已存在");
        }
    }

    /**
     * 校验版本号唯一
     *
     * @param documentId 文档 ID
     * @param versionNo  版本号
     * @param exclude    排除的版本 ID
     */
    private void assertVersionNoUnique(Long documentId, String versionNo, Long exclude) {
        LambdaQueryWrapper<PdmDocVersion> wrapper = new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, documentId)
                .eq(PdmDocVersion::getVersionNo, versionNo);
        if (exclude != null) {
            wrapper.ne(PdmDocVersion::getId, exclude);
        }
        if (versionMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "版本号已存在");
        }
    }

    /**
     * 校验文档读取权限：只有显式 DENY 才拒绝
     *
     * @param document 文档
     * @param viewer   访问者
     * @param action   动作
     */
    private void assertReadAccess(PdmDocDocument document, PdmDocViewer viewer, String action) {
        PdmDocAclDecision decision = aclResolver.decideDocument(document.getId(), document.getFolderId(),
                viewer, action);
        if (decision == PdmDocAclDecision.DENY) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该文档");
        }
    }

    /**
     * 校验文档写入权限：DENY 优先，ALLOW 放行，否则回落属主/管理员
     *
     * @param document  文档
     * @param viewer    访问者
     * @param action    动作
     * @param canManage 是否可管理他人文档
     */
    private void assertWriteAccess(PdmDocDocument document, PdmDocViewer viewer, String action,
                                   boolean canManage) {
        PdmDocAclDecision decision = aclResolver.decideDocument(document.getId(), document.getFolderId(),
                viewer, action);
        if (decision == PdmDocAclDecision.DENY) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该文档");
        }
        if (decision == PdmDocAclDecision.ALLOW) {
            return;
        }
        assertOwnerOrManager(document, viewer == null ? null : viewer.userId(), canManage);
    }

    /**
     * 校验当前用户可编辑文档
     *
     * @param document 文档
     * @param userId   当前用户
     * @param canManage 是否可管理他人文档
     */
    private void assertOwnerOrManager(PdmDocDocument document, Long userId, boolean canManage) {
        if (canManage) {
            return;
        }
        if (userId == null || !document.getOwnerId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作本人创建的文档");
        }
    }

    /**
     * 校验文档未被他人检出
     *
     * @param document 文档
     * @param userId   当前用户
     */
    private void assertNotCheckedOutByOther(PdmDocDocument document, Long userId) {
        if (document.getCheckedOutBy() != null && !document.getCheckedOutBy().equals(userId)) {
            throw new BizException(ErrorCode.REPEAT_SUBMIT, "文档已被他人检出");
        }
    }

    /**
     * 生成文档编号
     *
     * @return 文档编号
     */
    private String generateDocNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 5; i++) {
            String candidate = "DOC-" + date + "-" + String.format("%06d",
                    ThreadLocalRandom.current().nextInt(1_000_000));
            if (documentMapper.selectCount(new LambdaQueryWrapper<PdmDocDocument>()
                    .eq(PdmDocDocument::getDocNo, candidate)) == 0) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "文档编号生成失败，请重试");
    }

    /**
     * 计算 SHA-256 校验和
     *
     * @param content 文件字节
     * @return 十六进制校验和
     */
    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件校验和计算失败");
        }
    }

    /**
     * 清理文件名中的路径分隔符
     *
     * @param fileName 原始文件名
     * @return 安全文件名
     */
    private String safeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/]", "_").trim();
    }
}
