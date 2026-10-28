package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.file.aggregate.FileAsset;
import com.bluewhale.file.domain.file.repository.FileAssetPage;
import com.bluewhale.file.domain.file.repository.FileAssetQuery;
import com.bluewhale.file.domain.file.repository.FileAssetRepository;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileChangeVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 文件信息服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FileInfoServiceImpl implements FileInfoService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 对象键日期目录格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 对象键租户目录前缀（Wave 5.54 Phase 2：存储层按租户隔离） */
    private static final String TENANT_KEY_PREFIX = "tenants/";

    /** 原始文件名缺失时的占位名 */
    private static final String UNKNOWN_FILENAME = "unknown";

    /** 预签名默认有效期（秒） */
    private static final int DEFAULT_EXPIRES_SECONDS = 600;

    /** 预签名有效期上限（秒） */
    private static final int MAX_EXPIRES_SECONDS = 3600;

    /** 分片大小未配置或非法时的兜底值：5MB */
    private static final long DEFAULT_CHUNK_SIZE = 5242880L;

    /** 分片临时目录名（位于存储根目录下） */
    private static final String TMP_DIR_NAME = "tmp";

    /** 分片文件名前缀，分片文件名为 {@code part-<index>} */
    private static final String PART_FILE_PREFIX = "part-";

    /** 合并后临时文件名 */
    private static final String MERGED_FILE_NAME = "merged.bin";

    /** 上传会话元数据文件名 */
    private static final String META_FILE_NAME = "meta.properties";

    /** 元数据键：原始文件名 */
    private static final String META_KEY_FILE_NAME = "fileName";

    /** 元数据键：MIME 类型 */
    private static final String META_KEY_CONTENT_TYPE = "contentType";

    /** 元数据键：文件总字节数 */
    private static final String META_KEY_SIZE = "size";

    private final FileAssetRepository fileAssetRepository;

    private final FileStorage fileStorage;

    private final FileProperties fileProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadVO upload(MultipartFile file, String bucket, Long uploaderId) {
        validateFile(file);
        if (file.getSize() > fileProperties.getMaxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        validateQuota(file.getSize(), uploaderId);
        byte[] bytes = readBytes(file);
        return persistFile(new ByteArrayInputStream(bytes), file.getOriginalFilename(), file.getContentType(),
                bytes.length, DigestUtils.md5DigestAsHex(bytes), bucket, uploaderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileRapidUploadVO rapidUpload(FileRapidUploadRequest request, Long uploaderId) {
        validateDeclaredSize(request.getSize());
        String md5 = request.getMd5().toLowerCase(Locale.ROOT);
        FileRapidUploadVO vo = new FileRapidUploadVO();
        FileAsset existing = fileAssetRepository.findReusableByDigest(md5, request.getSize()).orElse(null);
        if (existing == null) {
            vo.setHit(false);
            return vo;
        }
        validateQuota(request.getSize(), uploaderId);
        FileAsset asset = FileAsset.reuse(existing, resolveOriginalName(request.getFileName()),
                request.getContentType(), uploaderId);
        FileAsset saved = fileAssetRepository.save(asset);
        vo.setHit(true);
        vo.setFile(toUploadVo(saved));
        return vo;
    }

    @Override
    public FileQuotaVO quota(Long uploaderId) {
        FileQuotaVO vo = new FileQuotaVO();
        long quota = fileProperties.getQuotaBytes();
        vo.setQuotaBytes(quota);
        if (uploaderId == null || quota <= 0) {
            vo.setUsedBytes(0L);
            vo.setRemainingBytes(0L);
            vo.setUnlimited(true);
            return vo;
        }
        long used = fileAssetRepository.sumNormalSizeByUploader(uploaderId);
        vo.setUsedBytes(used);
        vo.setRemainingBytes(Math.max(quota - used, 0L));
        vo.setUnlimited(false);
        return vo;
    }

    @Override
    public PageResult<FileInfoVO> page(FileQueryRequest request) {
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        FileAssetQuery query = new FileAssetQuery(request.getOriginalName(), request.getBucket(),
                request.getCreateTimeStart(), request.getCreateTimeEnd(), request.getCurrent(), size);
        FileAssetPage page = fileAssetRepository.page(query);
        List<FileInfoVO> records = page.records().stream().map(FileInfoServiceImpl::toVo).toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    public FileInfoVO getDetail(Long id) {
        return toVo(requireFile(id));
    }

    @Override
    public FileChangeVO getChangeDetail(Long id) {
        FileAsset asset = fileAssetRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        ensureTenantVisible(asset);
        return toChangeVo(asset);
    }

    @Override
    public Resource loadResource(Long id) {
        FileAsset asset = requireFile(id);
        return new InputStreamResource(fileStorage.load(asset.getBucket(), asset.getObjectKey()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FileAsset asset = requireFile(id);
        asset.assertDeletable();
        fileAssetRepository.deleteById(id);
    }

    @Override
    public PageResult<FileChangeVO> changes(LocalDateTime since, long current, long size) {
        long pageCurrent = Math.max(current, 1L);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        FileAssetPage page = fileAssetRepository.findChanges(since, pageCurrent, pageSize);
        List<FileChangeVO> records = page.records().stream().map(FileInfoServiceImpl::toChangeVo).toList();
        return PageResult.of(records, page.total(), page.current(), page.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long id) {
        FileAsset asset = requireFile(id);
        if (!asset.lock()) {
            return;
        }
        fileAssetRepository.save(asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        FileAsset asset = requireFile(id);
        if (!asset.unlock()) {
            return;
        }
        fileAssetRepository.save(asset);
    }

    @Override
    public FilePresignedVO presignedUrl(Long id, Integer expiresSeconds) {
        FileAsset asset = requireFile(id);
        int expires = resolveExpiresSeconds(expiresSeconds);
        FilePresignedVO vo = new FilePresignedVO();
        vo.setUrl(fileStorage.presignedDownloadUrl(asset.getBucket(), asset.getObjectKey(), expires));
        vo.setExpiresSeconds(expires);
        return vo;
    }

    @Override
    public ChunkUploadInitVO initChunkUpload(ChunkUploadInitRequest request) {
        long size = request.getSize();
        validateDeclaredSize(size);
        String uploadId = StringUtils.hasText(request.getUploadId())
                ? normalizeUploadId(request.getUploadId())
                : UUID.randomUUID().toString();
        Path dir = chunkDir(uploadId);
        try {
            // 同一 uploadId 重复 init 时保留已有分片，实现断点续传
            Files.createDirectories(dir);
            writeMeta(dir, request);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId(uploadId);
        vo.setChunkSize(resolveChunkSize());
        vo.setUploadedIndexes(listUploadedIndexes(dir));
        return vo;
    }

    @Override
    public List<Integer> uploadChunkPart(String uploadId, int index, MultipartFile file) {
        if (file == null || file.isEmpty() || index < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Path dir = requireChunkDir(uploadId);
        if (file.getSize() > resolveChunkSize()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分片大小超出上限");
        }
        Path target = dir.resolve(PART_FILE_PREFIX + index);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        return listUploadedIndexes(dir);
    }

    @Override
    public List<Integer> chunkUploadStatus(String uploadId) {
        return listUploadedIndexes(requireChunkDir(uploadId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadVO completeChunkUpload(ChunkUploadCompleteRequest request, Long uploaderId) {
        long size = request.getSize();
        validateDeclaredSize(size);
        validateQuota(size, uploaderId);
        Path dir = requireChunkDir(request.getUploadId());
        int expectedCount = expectedChunkCount(size);
        List<Integer> uploaded = listUploadedIndexes(dir);
        List<Integer> missing = IntStream.range(0, expectedCount)
                .filter(i -> !uploaded.contains(i))
                .boxed()
                .toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少分片索引: " + missing);
        }
        if (uploaded.size() != expectedCount) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分片数量与文件大小不匹配");
        }
        Path merged = dir.resolve(MERGED_FILE_NAME);
        try {
            mergeParts(dir, expectedCount, merged);
            if (Files.size(merged) != size) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "分片总大小与声明大小不一致");
            }
            String md5 = md5Of(merged);
            String contentType = readMetaContentType(dir);
            FileUploadVO vo;
            try (InputStream in = Files.newInputStream(merged)) {
                vo = persistFile(in, request.getFileName(), contentType, size, md5,
                        request.getBucket(), uploaderId);
            }
            // 仅在合并落存储成功后清理临时目录；失败时保留分片供客户端续传/重试
            deleteQuietly(dir);
            return vo;
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 将已就绪的内容写入存储并登记元数据（普通上传与分片合并共用）。
     *
     * @param in 内容输入流，由调用方负责关闭
     * @param originalName 原始文件名
     * @param contentType MIME 类型，可为 {@code null}
     * @param fileSize 内容字节数
     * @param md5 内容 MD5 摘要（十六进制小写）
     * @param bucket 目标桶名称；为空时取配置的默认桶
     * @param uploaderId 上传用户 ID；无登录态时可为 {@code null}
     * @return 上传结果视图对象
     * @throws BizException 大小超限时抛出 {@code PARAM_ERROR}
     */
    private FileUploadVO persistFile(InputStream in, String originalName, String contentType,
                                     long fileSize, String md5, String bucket, Long uploaderId) {
        if (fileSize > fileProperties.getMaxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件大小超出上限");
        }
        String name = resolveOriginalName(originalName);
        String resolvedBucket = StringUtils.hasText(bucket)
                ? bucket : fileProperties.getBucket().getDefault();
        String objectKey = buildObjectKey(name);
        fileStorage.store(resolvedBucket, objectKey, in);
        FileAsset asset = FileAsset.register(resolvedBucket, objectKey, name, fileSize, contentType, md5, uploaderId);
        return toUploadVo(fileAssetRepository.save(asset));
    }

    /**
     * 查询文件资产，不存在或跨租户时抛出 {@code NOT_FOUND}
     *
     * @param id 文件ID
     * @return 文件资产聚合根
     */
    private FileAsset requireFile(Long id) {
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        ensureTenantVisible(asset);
        return asset;
    }

    /**
     * 校验文件资产对当前租户可见，跨租户按不存在处理以避免泄露文件是否存在
     *
     * @param asset 文件资产聚合根
     * @throws BizException 跨租户访问时抛出 {@code NOT_FOUND}
     */
    private static void ensureTenantVisible(FileAsset asset) {
        if (!asset.isVisibleToTenant(TenantContext.get())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * 解析预签名有效期：非正数取默认值，超过上限则截断
     *
     * @param expiresSeconds 请求的有效期（秒），可为 {@code null}
     * @return 生效的有效期（秒）
     */
    private static int resolveExpiresSeconds(Integer expiresSeconds) {
        if (expiresSeconds == null || expiresSeconds <= 0) {
            return DEFAULT_EXPIRES_SECONDS;
        }
        return Math.min(expiresSeconds, MAX_EXPIRES_SECONDS);
    }

    /**
     * 校验声明的文件大小：必须大于 0 且不超过配置上限
     *
     * @param size 声明的文件字节数
     * @throws BizException 大小非法时抛出 {@code PARAM_ERROR}
     */
    private void validateDeclaredSize(Long size) {
        if (size == null || size <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件大小必须大于 0");
        }
        if (size > fileProperties.getMaxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件大小超出上限");
        }
    }

    /**
     * 校验个人存储配额：不限配额或无用户态时直接放行，否则拒绝超出配额的写入
     *
     * @param incomingSize 本次拟写入的字节数
     * @param uploaderId 上传用户 ID；无登录态时为 {@code null}
     * @throws BizException 超出个人存储配额时抛出 {@code PARAM_ERROR}
     */
    private void validateQuota(long incomingSize, Long uploaderId) {
        long quota = fileProperties.getQuotaBytes();
        if (quota <= 0 || uploaderId == null) {
            return;
        }
        long used = fileAssetRepository.sumNormalSizeByUploader(uploaderId);
        if (used + incomingSize > quota) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "超出个人存储配额");
        }
    }

    /**
     * 解析分片大小，配置非法时回落默认 5MB
     *
     * @return 分片大小（字节）
     */
    private long resolveChunkSize() {
        long configured = fileProperties.getChunkSizeBytes();
        return configured > 0 ? configured : DEFAULT_CHUNK_SIZE;
    }

    /**
     * 计算声明大小对应的分片数量（向上取整）
     *
     * @param size 文件总字节数
     * @return 期望的分片数量
     */
    private int expectedChunkCount(long size) {
        long chunkSize = resolveChunkSize();
        return (int) ((size + chunkSize - 1) / chunkSize);
    }

    /**
     * 规范化并校验 uploadId（UUID），防止路径穿越
     *
     * @param uploadId 调用方传入的 uploadId
     * @return 标准格式的 UUID 字符串
     * @throws BizException uploadId 非法时抛出 {@code PARAM_ERROR}
     */
    private static String normalizeUploadId(String uploadId) {
        try {
            return UUID.fromString(uploadId).toString();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "uploadId 非法");
        }
    }

    /**
     * 解析上传会话的临时目录 {@code ${root}/tmp/${uploadId}} 并做越界防护
     *
     * @param uploadId 上传会话 ID
     * @return 归一化后的临时目录路径
     */
    private Path chunkDir(String uploadId) {
        Path root = Paths.get(fileProperties.getStorage().getRoot()).normalize();
        Path dir = root.resolve(TMP_DIR_NAME).resolve(uploadId).normalize();
        if (!dir.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return dir;
    }

    /**
     * 获取已存在的上传会话临时目录，不存在时抛出参数错误
     *
     * @param uploadId 上传会话 ID
     * @return 临时目录路径
     * @throws BizException 会话不存在或已失效时抛出 {@code PARAM_ERROR}
     */
    private Path requireChunkDir(String uploadId) {
        Path dir = chunkDir(normalizeUploadId(uploadId));
        if (!Files.isDirectory(dir)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上传会话不存在或已失效");
        }
        return dir;
    }

    /**
     * 写入上传会话元数据（文件名、MIME 类型、总大小）
     *
     * @param dir 临时目录
     * @param request 初始化请求
     * @throws IOException 写文件失败时抛出
     */
    private static void writeMeta(Path dir, ChunkUploadInitRequest request) throws IOException {
        Properties meta = new Properties();
        meta.setProperty(META_KEY_FILE_NAME, request.getFileName());
        meta.setProperty(META_KEY_CONTENT_TYPE, request.getContentType() == null ? "" : request.getContentType());
        meta.setProperty(META_KEY_SIZE, String.valueOf(request.getSize()));
        try (OutputStream out = Files.newOutputStream(dir.resolve(META_FILE_NAME))) {
            meta.store(out, "chunk upload metadata");
        }
    }

    /**
     * 读取上传会话元数据中的 MIME 类型
     *
     * @param dir 临时目录
     * @return MIME 类型；元数据缺失或为空时返回 {@code null}
     */
    private static String readMetaContentType(Path dir) {
        Path metaFile = dir.resolve(META_FILE_NAME);
        if (!Files.exists(metaFile)) {
            return null;
        }
        Properties meta = new Properties();
        try (InputStream in = Files.newInputStream(metaFile)) {
            meta.load(in);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        String contentType = meta.getProperty(META_KEY_CONTENT_TYPE);
        return StringUtils.hasText(contentType) ? contentType : null;
    }

    /**
     * 列出临时目录中已上传的分片索引（升序，忽略元数据等非分片文件）
     *
     * @param dir 临时目录
     * @return 已上传分片索引；目录不存在时返回空列表
     */
    private static List<Integer> listUploadedIndexes(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            List<Integer> indexes = new ArrayList<>();
            paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(PART_FILE_PREFIX))
                    .map(name -> name.substring(PART_FILE_PREFIX.length()))
                    .filter(FileInfoServiceImpl::isNumeric)
                    .forEach(name -> indexes.add(Integer.valueOf(name)));
            indexes.sort(Comparator.naturalOrder());
            return indexes;
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 判断字符串是否全部由数字组成
     *
     * @param text 待校验字符串
     * @return 全部为数字时返回 {@code true}
     */
    private static boolean isNumeric(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按索引顺序将分片合并到目标文件
     *
     * @param dir 临时目录
     * @param count 分片数量
     * @param merged 合并后的文件路径
     * @throws IOException 读写失败时抛出
     */
    private static void mergeParts(Path dir, int count, Path merged) throws IOException {
        try (OutputStream out = Files.newOutputStream(merged)) {
            for (int i = 0; i < count; i++) {
                Files.copy(dir.resolve(PART_FILE_PREFIX + i), out);
            }
        }
    }

    /**
     * 计算文件 MD5 摘要
     *
     * @param path 文件路径
     * @return 十六进制小写 MD5
     * @throws BizException 读取失败时抛出 {@code SYSTEM_ERROR}
     */
    private static String md5Of(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return DigestUtils.md5DigestAsHex(in);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 尽力删除临时目录（含合并文件），失败不抛出，避免影响已完成的上传结果
     *
     * @param dir 临时目录
     */
    private static void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            // 清理失败不影响已完成的上传，仅残留临时目录
        }
    }

    /**
     * 校验上传文件非空
     *
     * @param file 上传的文件
     * @throws BizException 文件为空时抛出 {@code PARAM_ERROR}
     */
    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 读取上传文件内容为字节数组
     *
     * @param file 上传的文件
     * @return 文件内容字节数组
     * @throws BizException 读取失败时抛出 {@code SYSTEM_ERROR}
     */
    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 解析原始文件名，缺失时回落占位名
     *
     * @param rawName 上传文件原始名
     * @return 生效的原始文件名
     */
    private static String resolveOriginalName(String rawName) {
        return StringUtils.hasText(rawName) ? rawName : UNKNOWN_FILENAME;
    }

    /**
     * 生成对象键：{@code tenants/{tenantId}/yyyy/MM/dd/<uuid>.<ext>}
     *
     * @param originalName 原始文件名（用于提取扩展名）
     * @return 对象键
     */
    private static String buildObjectKey(String originalName) {
        String extension = StringUtils.getFilenameExtension(originalName);
        String suffix = StringUtils.hasText(extension) ? "." + extension : "";
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return TENANT_KEY_PREFIX + TenantContext.segment() + "/"
                + LocalDate.now().format(DATE_FORMATTER) + "/" + uuid + suffix;
    }

    /**
     * 文件资产转上传结果视图对象
     *
     * @param asset 文件资产聚合根
     * @return 上传结果视图对象
     */
    private static FileUploadVO toUploadVo(FileAsset asset) {
        FileUploadVO vo = new FileUploadVO();
        vo.setId(asset.getId());
        vo.setFileName(asset.getFileName());
        vo.setOriginalName(asset.getOriginalName());
        vo.setBucket(asset.getBucket());
        vo.setObjectKey(asset.getObjectKey());
        vo.setFileSize(asset.getFileSize());
        vo.setContentType(asset.getContentType());
        vo.setMd5(asset.getMd5());
        return vo;
    }

    /**
     * 文件资产转信息视图对象
     *
     * @param asset 文件资产聚合根
     * @return 文件信息视图对象
     */
    private static FileInfoVO toVo(FileAsset asset) {
        FileInfoVO vo = new FileInfoVO();
        vo.setId(asset.getId());
        vo.setFileName(asset.getFileName());
        vo.setOriginalName(asset.getOriginalName());
        vo.setBucket(asset.getBucket());
        vo.setObjectKey(asset.getObjectKey());
        vo.setFileSize(asset.getFileSize());
        vo.setContentType(asset.getContentType());
        vo.setMd5(asset.getMd5());
        vo.setUploaderId(asset.getUploaderId());
        vo.setCreateTime(asset.getCreateTime());
        return vo;
    }

    /**
     * 文件资产转变更流视图对象
     *
     * @param asset 文件资产聚合根
     * @return 文件变更视图对象
     */
    private static FileChangeVO toChangeVo(FileAsset asset) {
        FileChangeVO vo = new FileChangeVO();
        vo.setId(asset.getId());
        vo.setFileName(asset.getFileName());
        vo.setOriginalName(asset.getOriginalName());
        vo.setBucket(asset.getBucket());
        vo.setObjectKey(asset.getObjectKey());
        vo.setFileSize(asset.getFileSize());
        vo.setContentType(asset.getContentType());
        vo.setMd5(asset.getMd5());
        vo.setStatus(asset.getStatus().getCode());
        vo.setUploaderId(asset.getUploaderId());
        vo.setDeleted(asset.isDeleted() ? 1 : 0);
        vo.setCreateTime(asset.getCreateTime());
        vo.setUpdateTime(asset.getUpdateTime());
        return vo;
    }
}
