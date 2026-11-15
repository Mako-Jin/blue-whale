package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.result.FileArchiveEntryResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.service.FileArchiveService;
import com.bluewhale.file.service.FileInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件打包服务实现：流式 ZIP，不落本地临时文件。
 *
 * <p><b>安全边界</b>：条目名只取原始文件名最后一段并剔除控制字符，杜绝解压时的目录穿越；
 * 同名条目（忽略大小写）追加序号，避免解压覆盖。所有文件在写出响应前完成可见性校验，
 * 不可见文件按不存在整体拒绝，不泄露部分打包结果。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileArchiveServiceImpl implements FileArchiveService {

    /** 流式复制缓冲区字节数 */
    private static final int COPY_BUFFER_BYTES = 8192;

    /** 文件名缺失或非法时的条目占位名 */
    private static final String DEFAULT_ENTRY_NAME = "file";

    /** 重名条目序号后缀格式 */
    private static final String DUPLICATE_SUFFIX_FORMAT = " (%d)";

    /** 控制字符（含换行、制表符等），文件名中出现会破坏响应头与解压工具展示 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    /** 扩展名分隔符 */
    private static final char EXTENSION_SEPARATOR = '.';

    private final FileInfoService fileInfoService;

    private final FileProperties fileProperties;

    @Override
    public List<FileArchiveEntryResult> prepare(List<Long> ids, Long requesterId, boolean canManage) {
        List<Long> uniqueIds = distinctIds(ids);
        FileProperties.Batch config = fileProperties.getBatch();
        if (uniqueIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件ID列表不能为空");
        }
        if (uniqueIds.size() > config.getMaxFiles()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "单次最多打包 " + config.getMaxFiles() + " 个文件");
        }
        List<FileArchiveEntryResult> entries = new ArrayList<>(uniqueIds.size());
        Set<String> usedNames = new HashSet<>(uniqueIds.size());
        long totalBytes = 0L;
        for (Long id : uniqueIds) {
            FileInfoResult info = fileInfoService.getDetail(id, requesterId, canManage);
            long size = info.fileSize() == null ? 0L : info.fileSize();
            totalBytes += size;
            if (totalBytes > config.getMaxZipBytes()) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "打包文件总大小超出上限");
            }
            entries.add(new FileArchiveEntryResult(id,
                    dedupeName(safeEntryName(info.originalName()), usedNames), size));
        }
        return entries;
    }

    @Override
    public void writeZip(List<FileArchiveEntryResult> entries, Long requesterId, boolean canManage,
                         OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            for (FileArchiveEntryResult entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.entryName()));
                try (InputStream content = fileInfoService.openContent(entry.id(), requesterId, canManage)) {
                    int read = content.read(buffer);
                    while (read != -1) {
                        zip.write(buffer, 0, read);
                        read = content.read(buffer);
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            log.warn("打包下载写出失败，响应可能不完整", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 生成安全的 ZIP 条目名：只取文件名最后一段，剔除控制字符
     *
     * @param originalName 原始文件名，可为 {@code null}
     * @return 安全的条目名（非空）
     */
    private static String safeEntryName(String originalName) {
        String name = StringUtils.hasText(originalName) ? originalName : DEFAULT_ENTRY_NAME;
        name = name.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = CONTROL_CHARS.matcher(name).replaceAll("_").trim();
        if (!StringUtils.hasText(name) || ".".equals(name) || "..".equals(name)) {
            return DEFAULT_ENTRY_NAME;
        }
        return name;
    }

    /**
     * 条目名去重：忽略大小写判重，重复时在扩展名前插入序号
     *
     * @param name 安全条目名
     * @param usedNames 已占用的条目名（小写）
     * @return 去重后的条目名
     */
    private static String dedupeName(String name, Set<String> usedNames) {
        String candidate = name;
        int index = 1;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = insertSuffix(name, index);
            index++;
        }
        return candidate;
    }

    /**
     * 在扩展名前插入重名序号；无扩展名或扩展名位于首位时直接追加
     *
     * @param name 原始条目名
     * @param index 序号
     * @return 带序号的条目名
     */
    private static String insertSuffix(String name, int index) {
        String suffix = String.format(DUPLICATE_SUFFIX_FORMAT, index);
        int dot = name.lastIndexOf(EXTENSION_SEPARATOR);
        return dot > 0
                ? name.substring(0, dot) + suffix + name.substring(dot)
                : name + suffix;
    }

    /**
     * 去重并校验待打包的文件ID：保留请求顺序，拒绝空 ID
     *
     * @param ids 请求的文件ID列表，可为 {@code null}
     * @return 去重后的文件ID列表
     * @throws BizException 含空 ID 时抛出 {@code PARAM_ERROR}
     */
    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>(ids.size());
        for (Long id : ids) {
            if (id == null) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件ID不能为空");
            }
            unique.add(id);
        }
        return List.copyOf(unique);
    }
}
