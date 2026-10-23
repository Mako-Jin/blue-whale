package com.bluewhale.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.entity.FileInfo;
import com.bluewhale.file.mapper.FileInfoMapper;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.storage.FileStorage;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileUploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /** 文件状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 对象键日期目录格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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

    private final FileInfoMapper fileInfoMapper;

    private final FileStorage fileStorage;

    private final FileProperties fileProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadVO upload(MultipartFile file, String bucket, Long uploaderId) {
        validateFile(file);
        if (file.getSize() > fileProperties.getMaxSizeBytes()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        byte[] bytes = readBytes(file);
        return persistFile(new ByteArrayInputStream(bytes), file.getOriginalFilename(), file.getContentType(),
                bytes.length, DigestUtils.md5DigestAsHex(bytes), bucket, uploaderId);
    }

    @Override
    public PageResult<FileInfoVO> page(FileQueryRequest request) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getOriginalName()),
                FileInfo::getOriginalName, request.getOriginalName());
        wrapper.eq(StringUtils.hasText(request.getBucket()), FileInfo::getBucket, request.getBucket());
        wrapper.ge(request.getCreateTimeStart() != null, FileInfo::getCreateTime, request.getCreateTimeStart());
        wrapper.le(request.getCreateTimeEnd() != null, FileInfo::getCreateTime, request.getCreateTimeEnd());
        wrapper.orderByDesc(FileInfo::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<FileInfo> page = fileInfoMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<FileInfoVO> records = page.getRecords().stream().map(FileInfoServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public FileInfoVO getDetail(Long id) {
        return toVo(requireFile(id));
    }

    @Override
    public Resource loadResource(Long id) {
        FileInfo entity = requireFile(id);
        return new InputStreamResource(fileStorage.load(entity.getBucket(), entity.getObjectKey()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireFile(id);
        fileInfoMapper.deleteById(id);
    }

    @Override
    public FilePresignedVO presignedUrl(Long id, Integer expiresSeconds) {
        FileInfo entity = requireFile(id);
        int expires = resolveExpiresSeconds(expiresSeconds);
        FilePresignedVO vo = new FilePresignedVO();
        vo.setUrl(fileStorage.presignedDownloadUrl(entity.getBucket(), entity.getObjectKey(), expires));
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
        FileInfo entity = new FileInfo();
        entity.setFileName(objectKey.substring(objectKey.lastIndexOf('/') + 1));
        entity.setOriginalName(name);
        entity.setBucket(resolvedBucket);
        entity.setObjectKey(objectKey);
        entity.setFileSize(fileSize);
        entity.setContentType(contentType);
        entity.setMd5(md5);
        entity.setStatus(STATUS_NORMAL);
        entity.setUploaderId(uploaderId);
        fileInfoMapper.insert(entity);
        return toUploadVo(entity);
    }

    /**
     * 查询文件元数据，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 文件ID
     * @return 文件实体
     */
    private FileInfo requireFile(Long id) {
        FileInfo entity = fileInfoMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
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
     * 生成对象键：{@code yyyy/MM/dd/<uuid>.<ext>}
     *
     * @param originalName 原始文件名（用于提取扩展名）
     * @return 对象键
     */
    private static String buildObjectKey(String originalName) {
        String extension = StringUtils.getFilenameExtension(originalName);
        String suffix = StringUtils.hasText(extension) ? "." + extension : "";
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return LocalDate.now().format(DATE_FORMATTER) + "/" + uuid + suffix;
    }

    /**
     * 文件实体转上传结果视图对象
     *
     * @param entity 文件实体
     * @return 上传结果视图对象
     */
    private static FileUploadVO toUploadVo(FileInfo entity) {
        FileUploadVO vo = new FileUploadVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 文件实体转信息视图对象
     *
     * @param entity 文件实体
     * @return 文件信息视图对象
     */
    private static FileInfoVO toVo(FileInfo entity) {
        FileInfoVO vo = new FileInfoVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
