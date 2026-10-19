package com.bluewhale.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.entity.FileInfo;
import com.bluewhale.file.mapper.FileInfoMapper;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.storage.FileStorage;
import com.bluewhale.file.vo.FileInfoVO;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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
        String originalName = resolveOriginalName(file.getOriginalFilename());
        String resolvedBucket = StringUtils.hasText(bucket)
                ? bucket : fileProperties.getBucket().getDefault();
        String objectKey = buildObjectKey(originalName);
        fileStorage.store(resolvedBucket, objectKey, new ByteArrayInputStream(bytes));
        FileInfo entity = new FileInfo();
        entity.setFileName(objectKey.substring(objectKey.lastIndexOf('/') + 1));
        entity.setOriginalName(originalName);
        entity.setBucket(resolvedBucket);
        entity.setObjectKey(objectKey);
        entity.setFileSize((long) bytes.length);
        entity.setContentType(file.getContentType());
        entity.setMd5(DigestUtils.md5DigestAsHex(bytes));
        entity.setStatus(STATUS_NORMAL);
        entity.setUploaderId(uploaderId);
        fileInfoMapper.insert(entity);
        return toUploadVo(entity);
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
