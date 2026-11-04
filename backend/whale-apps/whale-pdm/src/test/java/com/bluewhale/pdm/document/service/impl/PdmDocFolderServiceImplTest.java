package com.bluewhale.pdm.document.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.document.dto.PdmDocFolderSaveRequest;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocFolder;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocFolderMapper;
import com.bluewhale.pdm.document.vo.PdmDocFolderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图文档目录服务单元测试。
 *
 * <p>覆盖建目录、同级重名、移动防环、删除约束、目录树装配与子树展开。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmDocFolderServiceImplTest {

    @Mock
    private PdmDocFolderMapper folderMapper;

    @Mock
    private PdmDocDocumentMapper documentMapper;

    @InjectMocks
    private PdmDocFolderServiceImpl folderService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PdmDocFolder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PdmDocDocument.class);
    }

    /**
     * 新建根目录：写入后回填 ID 与路径。
     */
    @Test
    @DisplayName("新建根目录生成 /{id}/ 路径")
    void createShouldGeneratePath() {
        when(folderMapper.selectCount(any())).thenReturn(0L);
        when(folderMapper.insert(any(PdmDocFolder.class))).thenAnswer(invocation -> {
            PdmDocFolder folder = invocation.getArgument(0);
            folder.setId(10L);
            return 1;
        });

        PdmDocFolderVO view = folderService.create(request(null, "主机装配图"));

        assertEquals(10L, view.getId());
        assertEquals("/10/", view.getPath());
        assertEquals(0L, view.getParentId());

        ArgumentCaptor<PdmDocFolder> captor = ArgumentCaptor.forClass(PdmDocFolder.class);
        verify(folderMapper).insert(captor.capture());
        assertEquals("CUSTOM", captor.getValue().getFolderType());
        verify(folderMapper).updateById(any(PdmDocFolder.class));
    }

    /**
     * 同级目录重名被拒绝且不写库。
     */
    @Test
    @DisplayName("同级重名拒绝创建")
    void createShouldRejectDuplicatedName() {
        when(folderMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> folderService.create(request(null, "主机装配图")));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(folderMapper, never()).insert(any(PdmDocFolder.class));
    }

    /**
     * 不能把目录移动到自身后代目录下。
     */
    @Test
    @DisplayName("移动目录到子目录被拒绝")
    void updateShouldRejectMoveIntoDescendant() {
        PdmDocFolder folder = folder(1L, 0L, "/1/", "产品图纸");
        PdmDocFolder child = folder(2L, 1L, "/1/2/", "子目录");
        when(folderMapper.selectById(1L)).thenReturn(folder);
        when(folderMapper.selectById(2L)).thenReturn(child);

        PdmDocFolderSaveRequest request = request(2L, "产品图纸");

        BizException exception = assertThrows(BizException.class,
                () -> folderService.update(1L, request));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(folderMapper, never()).updateById(any(PdmDocFolder.class));
    }

    /**
     * 存在子目录时不可删除。
     */
    @Test
    @DisplayName("含子目录不可删除")
    void deleteShouldRejectWhenChildrenExist() {
        when(folderMapper.selectById(1L)).thenReturn(folder(1L, 0L, "/1/", "产品图纸"));
        when(folderMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> folderService.delete(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(folderMapper, never()).deleteById(1L);
    }

    /**
     * 存在文档时不可删除。
     */
    @Test
    @DisplayName("含文档不可删除")
    void deleteShouldRejectWhenDocumentsExist() {
        when(folderMapper.selectById(1L)).thenReturn(folder(1L, 0L, "/1/", "产品图纸"));
        when(folderMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> folderService.delete(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(folderMapper, never()).deleteById(1L);
    }

    /**
     * 空目录可删除。
     */
    @Test
    @DisplayName("空目录可删除")
    void deleteShouldRemoveEmptyFolder() {
        when(folderMapper.selectById(1L)).thenReturn(folder(1L, 0L, "/1/", "产品图纸"));
        when(folderMapper.selectCount(any())).thenReturn(0L);

        folderService.delete(1L);

        verify(folderMapper).deleteById(1L);
    }

    /**
     * 目录树按父子关系装配并保持排序。
     */
    @Test
    @DisplayName("目录树按父子关系装配")
    void treeShouldAssembleHierarchy() {
        when(folderMapper.selectCount(any())).thenReturn(1L);
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(1L, 0L, "/1/", "产品图纸"),
                folder(2L, 1L, "/1/2/", "子目录"),
                folder(3L, 0L, "/3/", "测试报告")));

        List<PdmDocFolderVO> tree = folderService.tree();

        assertEquals(2, tree.size());
        assertEquals("产品图纸", tree.get(0).getName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("子目录", tree.get(0).getChildren().get(0).getName());
        assertEquals("测试报告", tree.get(1).getName());
    }

    /**
     * 子树 ID 按 path 前缀展开（含自身）。
     */
    @Test
    @DisplayName("子树展开包含自身与后代")
    void subtreeFolderIdsShouldIncludeDescendants() {
        when(folderMapper.selectById(1L)).thenReturn(folder(1L, 0L, "/1/", "产品图纸"));
        when(folderMapper.selectList(any())).thenReturn(List.of(
                folder(1L, 0L, "/1/", "产品图纸"),
                folder(2L, 1L, "/1/2/", "子目录"),
                folder(3L, 0L, "/3/", "测试报告")));

        List<Long> ids = folderService.subtreeFolderIds(1L);

        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(1L, 2L)));
    }

    /**
     * 构造目录保存请求。
     *
     * @param parentId 上级目录
     * @param name     目录名
     * @return 保存请求
     */
    private PdmDocFolderSaveRequest request(Long parentId, String name) {
        PdmDocFolderSaveRequest request = new PdmDocFolderSaveRequest();
        request.setParentId(parentId);
        request.setName(name);
        request.setSortNo(1);
        return request;
    }

    /**
     * 构造目录实体。
     *
     * @param id       主键
     * @param parentId 上级目录
     * @param path     路径
     * @param name     名称
     * @return 目录实体
     */
    private PdmDocFolder folder(Long id, Long parentId, String path, String name) {
        PdmDocFolder folder = new PdmDocFolder();
        folder.setId(id);
        folder.setParentId(parentId);
        folder.setPath(path);
        folder.setName(name);
        folder.setFolderType("CUSTOM");
        folder.setSortNo(id.intValue());
        return folder;
    }
}
