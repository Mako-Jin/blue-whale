package com.bluewhale.pdm.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.converter.PdmConverter;
import com.bluewhale.pdm.document.dto.PdmDocFolderSaveRequest;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocFolder;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocFolderMapper;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.vo.PdmDocFolderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 图文档目录服务实现
 *
 * <p>目录以 {@code path} 维护层级关系；移动目录时递归重写子树路径，
 * 保证 ACL 继承与子树查询口径一致。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmDocFolderServiceImpl implements PdmDocFolderService {

    /** 根目录 ID */
    private static final long ROOT_ID = 0L;

    /** 默认目录名称 */
    private static final String DEFAULT_PRODUCT_FOLDER = "产品图纸";

    /** 默认技术文档目录 */
    private static final String DEFAULT_DOC_FOLDER = "技术文档";

    /** 默认测试报告目录 */
    private static final String DEFAULT_REPORT_FOLDER = "测试报告";

    private final PdmDocFolderMapper folderMapper;

    private final PdmDocDocumentMapper documentMapper;

    @Override
    public List<PdmDocFolderVO> tree() {
        ensureDefaultFolders();
        List<PdmDocFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<PdmDocFolder>()
                .orderByAsc(PdmDocFolder::getSortNo)
                .orderByAsc(PdmDocFolder::getId));
        Map<Long, PdmDocFolderVO> nodes = new LinkedHashMap<>();
        for (PdmDocFolder folder : folders) {
            PdmDocFolderVO node = PdmConverter.INSTANCE.toFolderVo(folder);
            node.setChildren(new ArrayList<>());
            nodes.put(node.getId(), node);
        }
        List<PdmDocFolderVO> roots = new ArrayList<>();
        for (PdmDocFolderVO node : nodes.values()) {
            PdmDocFolderVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null || ROOT_ID == node.getParentId()) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        roots.sort(Comparator.comparing(PdmDocFolderVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PdmDocFolderVO::getId));
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocFolderVO create(PdmDocFolderSaveRequest request) {
        Long parentId = normalizeParentId(request.getParentId());
        PdmDocFolder parent = requireParent(parentId);
        assertNameUnique(parentId, request.getName().trim(), null);
        PdmDocFolder folder = PdmConverter.INSTANCE.toFolderEntity(request);
        folder.setName(request.getName().trim());
        folder.setParentId(parentId);
        folder.setFolderType(defaultText(request.getFolderType(), "CUSTOM"));
        folder.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        folderMapper.insert(folder);
        folder.setPath(parentPath(parent) + folder.getId() + "/");
        folderMapper.updateById(folder);
        return toVo(folder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocFolderVO update(Long id, PdmDocFolderSaveRequest request) {
        PdmDocFolder folder = requireFolder(id);
        Long newParentId = normalizeParentId(request.getParentId());
        PdmDocFolder parent = requireParent(newParentId);
        if (!newParentId.equals(folder.getParentId())) {
            assertNotDescendant(folder, parent);
        }
        assertNameUnique(newParentId, request.getName().trim(), id);
        String oldPath = folder.getPath();
        String newPath = parentPath(parent) + id + "/";
        folder.setParentId(newParentId);
        folder.setName(request.getName().trim());
        folder.setFolderType(defaultText(request.getFolderType(), folder.getFolderType()));
        folder.setSortNo(request.getSortNo() == null ? folder.getSortNo() : request.getSortNo());
        folder.setRemark(request.getRemark());
        folder.setPath(newPath);
        folderMapper.updateById(folder);
        if (oldPath != null && !oldPath.equals(newPath)) {
            rewriteSubtreePath(oldPath, newPath);
        }
        return toVo(folder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PdmDocFolder folder = requireFolder(id);
        long childCount = folderMapper.selectCount(new LambdaQueryWrapper<PdmDocFolder>()
                .eq(PdmDocFolder::getParentId, id));
        if (childCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.there-sub-directory-under-directory-cannot", "目录下存在子目录，不能删除");
        }
        long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<PdmDocDocument>()
                .eq(PdmDocDocument::getFolderId, id));
        if (documentCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.there-document-under-directory-cannot-delete", "目录下存在文档，不能删除");
        }
        folderMapper.deleteById(folder.getId());
    }

    @Override
    public PdmDocFolderVO get(Long id) {
        return toVo(requireFolder(id));
    }

    @Override
    public List<Long> subtreeFolderIds(Long folderId) {
        PdmDocFolder folder = requireFolder(folderId);
        return subtreeFolderIds(List.of(folder.getId()));
    }

    @Override
    public List<Long> subtreeFolderIds(Collection<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        List<PdmDocFolder> all = folderMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, PdmDocFolder> byId = new LinkedHashMap<>();
        for (PdmDocFolder folder : all) {
            byId.put(folder.getId(), folder);
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long folderId : folderIds) {
            PdmDocFolder root = byId.get(folderId);
            if (root == null) {
                continue;
            }
            ids.add(root.getId());
            String prefix = root.getPath();
            if (prefix == null) {
                continue;
            }
            for (PdmDocFolder item : all) {
                if (item.getPath() != null && item.getPath().startsWith(prefix)) {
                    ids.add(item.getId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    @Override
    public List<Long> ancestorFolderIds(Long folderId) {
        PdmDocFolder folder = requireFolder(folderId);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        parsePathIds(folder.getPath(), ids);
        if (!ids.contains(folder.getId())) {
            List<Long> chain = new ArrayList<>();
            Long cursor = folder.getId();
            while (cursor != null && ROOT_ID != cursor && !chain.contains(cursor)) {
                chain.add(0, cursor);
                PdmDocFolder current = folderMapper.selectById(cursor);
                cursor = current == null ? null : current.getParentId();
            }
            ids.addAll(chain);
        }
        return new ArrayList<>(ids);
    }

    /**
     * 解析目录路径中的 ID 链
     *
     * @param path 目录路径，形如 {@code /1/2/}
     * @param ids  输出集合
     */
    private void parsePathIds(String path, LinkedHashSet<Long> ids) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        for (String segment : path.split("/")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            try {
                ids.add(Long.valueOf(segment.trim()));
            } catch (NumberFormatException ignored) {
                // 非ID路径段不参与 ACL 继承
            }
        }
    }

    /**
     * 租户首次访问时初始化默认目录
     */
    private void ensureDefaultFolders() {
        long count = folderMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > 0) {
            return;
        }
        createDefaultFolder(DEFAULT_PRODUCT_FOLDER, "PRODUCT", 10);
        createDefaultFolder(DEFAULT_DOC_FOLDER, "DOC_TYPE", 20);
        createDefaultFolder(DEFAULT_REPORT_FOLDER, "CUSTOM", 30);
    }

    /**
     * 创建默认根目录
     *
     * @param name       目录名
     * @param folderType 类型
     * @param sortNo     排序
     */
    private void createDefaultFolder(String name, String folderType, int sortNo) {
        PdmDocFolder folder = new PdmDocFolder();
        folder.setParentId(ROOT_ID);
        folder.setName(name);
        folder.setFolderType(folderType);
        folder.setSortNo(sortNo);
        folder.setRemark("系统默认目录");
        folderMapper.insert(folder);
        folder.setPath("/" + folder.getId() + "/");
        folderMapper.updateById(folder);
        log.info("PDM 图文档默认目录已创建: tenant={}, id={}, name={}",
                TenantContext.requireId(), folder.getId(), name);
    }

    /**
     * 校验并返回上级目录
     *
     * @param parentId 上级目录 ID
     * @return 上级目录；根目录返回 {@code null}
     */
    private PdmDocFolder requireParent(Long parentId) {
        if (ROOT_ID == parentId) {
            return null;
        }
        return requireFolder(parentId);
    }

    /**
     * 校验目录存在
     *
     * @param id 目录 ID
     * @return 目录实体
     */
    private PdmDocFolder requireFolder(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.document.directory-found", "目录不存在");
        }
        PdmDocFolder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.document.directory-found-2", "目录不存在");
        }
        return folder;
    }

    /**
     * 校验同级目录名称唯一
     *
     * @param parentId 上级目录 ID
     * @param name     目录名
     * @param exclude  排除的目录 ID
     */
    private void assertNameUnique(Long parentId, String name, Long exclude) {
        LambdaQueryWrapper<PdmDocFolder> wrapper = new LambdaQueryWrapper<PdmDocFolder>()
                .eq(PdmDocFolder::getParentId, parentId)
                .eq(PdmDocFolder::getName, name);
        if (exclude != null) {
            wrapper.ne(PdmDocFolder::getId, exclude);
        }
        if (folderMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.same-level-directory-name-already-exists", "同级目录名称已存在");
        }
    }

    /**
     * 禁止把目录移动到自身或后代目录下
     *
     * @param folder 待移动目录
     * @param parent 目标上级目录
     */
    private void assertNotDescendant(PdmDocFolder folder, PdmDocFolder parent) {
        if (parent == null) {
            return;
        }
        if (parent.getId().equals(folder.getId()) || parent.getPath().startsWith(folder.getPath())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.cannot-move-directory-under-itself-sub", "不能把目录移动到自身或子目录下");
        }
    }

    /**
     * 递归重写子树路径
     *
     * @param oldPath 旧前缀
     * @param newPath 新前缀
     */
    private void rewriteSubtreePath(String oldPath, String newPath) {
        List<PdmDocFolder> descendants = folderMapper.selectList(new LambdaQueryWrapper<PdmDocFolder>()
                .likeRight(PdmDocFolder::getPath, oldPath));
        for (PdmDocFolder descendant : descendants) {
            if (descendant.getPath() == null || descendant.getPath().equals(oldPath)) {
                continue;
            }
            descendant.setPath(newPath + descendant.getPath().substring(oldPath.length()));
            folderMapper.updateById(descendant);
        }
    }

    /**
     * 计算上级目录路径
     *
     * @param parent 上级目录
     * @return 路径前缀
     */
    private String parentPath(PdmDocFolder parent) {
        return parent == null ? "/" : parent.getPath();
    }

    /**
     * 目录实体转视图
     *
     * @param folder 目录实体
     * @return 目录视图
     */
    private PdmDocFolderVO toVo(PdmDocFolder folder) {
        return PdmConverter.INSTANCE.toFolderVo(folder);
    }

    /**
     * 归一化上级目录 ID
     *
     * @param parentId 原始上级目录 ID
     * @return 非空上级目录 ID
     */
    private Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_ID : parentId;
    }

    /**
     * 文本为空时使用默认值
     *
     * @param value       原始文本
     * @param defaultValue 默认值
     * @return 有效文本
     */
    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
