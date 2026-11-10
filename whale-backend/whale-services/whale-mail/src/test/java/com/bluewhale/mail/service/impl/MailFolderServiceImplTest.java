package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.mapper.MailFolderMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.support.MailImapSupport;
import com.bluewhale.mail.support.RemoteFolder;
import com.bluewhale.mail.vo.MailFolderSyncVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件夹与服务器对齐测试。
 *
 * <p>覆盖四类容易出错的场景：自定义文件夹接入、系统文件夹改名后位点必须归零、
 * 服务器删除后本地下线、同名嵌套与顶层冲突时认领顶层。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailFolderServiceImplTest {

    /** 测试账号ID */
    private static final long ACCOUNT_ID = 1L;

    @Mock
    private MailFolderMapper folderMapper;

    @Mock
    private MailMessageMapper messageMapper;

    @Mock
    private MailAccountMapper accountMapper;

    @Mock
    private MailImapSupport imapSupport;

    private MailFolderServiceImpl service;

    private MailAccount account;

    /**
     * 初始化被测对象与账号
     *
     * <p>{@code LambdaUpdateWrapper} 依赖 MyBatis-Plus 的实体元数据缓存，纯单测没有
     * Spring 上下文时必须手动注册一次，否则构造 wrapper 会抛「找不到 lambda cache」。</p>
     */
    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MailFolder.class);
        service = new MailFolderServiceImpl(folderMapper, messageMapper, accountMapper, imapSupport);
        account = new MailAccount();
        account.setId(ACCOUNT_ID);
    }

    /**
     * 服务器上多出来的可选节点应接入为自定义文件夹，纯容器节点应被忽略
     */
    @Test
    @DisplayName("新自定义文件夹接入，容器节点忽略")
    void shouldAdoptNewCustomFolder() {
        when(folderMapper.selectList(any())).thenReturn(systemFolders());
        when(imapSupport.listFolders(account)).thenReturn(List.of(
                new RemoteFolder("INBOX", null, true),
                new RemoteFolder("Drafts", null, true),
                new RemoteFolder("Sent", null, true),
                new RemoteFolder("Junk", null, true),
                new RemoteFolder("Trash", null, true),
                new RemoteFolder("Archive", null, false),
                new RemoteFolder("Work", null, true)));

        MailFolderSyncVO result = service.syncFromServer(account);

        assertEquals(1, result.getAdded());
        ArgumentCaptor<MailFolder> captor = ArgumentCaptor.forClass(MailFolder.class);
        verify(folderMapper).insert(captor.capture());
        MailFolder inserted = captor.getValue();
        assertEquals("Work", inserted.getImapName());
        assertEquals("CUSTOM", inserted.getFolderType());
        assertEquals("CUSTOM", inserted.getFolderCode());
        assertEquals(0L, inserted.getLastUid());
        assertEquals(ACCOUNT_ID, inserted.getAccountId());
    }

    /**
     * 层级路径应去掉 INBOX 前缀并把分隔符换成空格斜杠
     */
    @Test
    @DisplayName("层级文件夹展示名去除 INBOX 前缀")
    void shouldBuildNestedDisplayName() {
        when(folderMapper.selectList(any())).thenReturn(systemFolders());
        when(imapSupport.listFolders(account)).thenReturn(List.of(
                new RemoteFolder("INBOX", ".", true),
                new RemoteFolder("INBOX.Drafts", ".", true),
                new RemoteFolder("INBOX.Sent", ".", true),
                new RemoteFolder("INBOX.Junk", ".", true),
                new RemoteFolder("INBOX.Trash", ".", true),
                new RemoteFolder("INBOX.Work.2024", ".", true)));

        service.syncFromServer(account);

        ArgumentCaptor<MailFolder> captor = ArgumentCaptor.forClass(MailFolder.class);
        verify(folderMapper).insert(captor.capture());
        assertEquals("Work / 2024", captor.getValue().getName());
    }

    /**
     * 服务器把已发送叫成 Sent Items 时必须纠正映射，且位点归零，否则新文件夹的 UID
     * 会被旧位点整段跳过，历史邮件永远同步不到
     */
    @Test
    @DisplayName("系统文件夹改名后纠正映射并重置位点")
    void shouldCorrectSystemFolderImapName() {
        List<MailFolder> local = systemFolders();
        MailFolder sent = local.get(2);
        sent.setImapName("Sent");
        sent.setLastUid(100L);
        sent.setUidValidity(7L);
        when(folderMapper.selectList(any())).thenReturn(local);
        when(imapSupport.listFolders(account)).thenReturn(List.of(
                new RemoteFolder("INBOX", null, true),
                new RemoteFolder("Drafts", null, true),
                new RemoteFolder("Sent Items", null, true),
                new RemoteFolder("Junk", null, true),
                new RemoteFolder("Trash", null, true)));

        MailFolderSyncVO result = service.syncFromServer(account);

        assertEquals(1, result.getUpdated());
        verify(folderMapper).updateById(sent);
        assertEquals("Sent Items", sent.getImapName());
        assertEquals(0L, sent.getLastUid());
        assertNull(sent.getUidValidity());
    }

    /**
     * 服务器上消失的自定义文件夹应下线，系统文件夹即使探测不到也必须保留
     */
    @Test
    @DisplayName("消失的自定义文件夹下线，系统文件夹保留")
    void shouldRetireVanishedCustomFolder() {
        List<MailFolder> local = systemFolders();
        MailFolder custom = new MailFolder();
        custom.setId(99L);
        custom.setAccountId(ACCOUNT_ID);
        custom.setFolderCode("CUSTOM");
        custom.setFolderType("CUSTOM");
        custom.setName("Old");
        custom.setImapName("Old");
        custom.setLastUid(5L);
        local.add(custom);
        when(folderMapper.selectList(any())).thenReturn(local);
        // 服务器只剩收件箱：其余系统文件夹探测不到，但不应被下线
        when(imapSupport.listFolders(account)).thenReturn(List.of(new RemoteFolder("INBOX", null, true)));

        MailFolderSyncVO result = service.syncFromServer(account);

        assertEquals(1, result.getRemoved());
        verify(folderMapper).update(any(), any());
        verify(folderMapper, never()).updateById(custom);
    }

    /**
     * 顶层与嵌套同名时认领顶层：用户可能在自己建的归档目录里放了同名子文件夹
     */
    @Test
    @DisplayName("同名冲突时认领顶层文件夹")
    void shouldPreferTopLevelCandidate() {
        List<MailFolder> local = systemFolders();
        MailFolder sent = local.get(2);
        sent.setImapName("Sent-Legacy");
        when(folderMapper.selectList(any())).thenReturn(local);
        when(imapSupport.listFolders(account)).thenReturn(List.of(
                new RemoteFolder("INBOX", ".", true),
                new RemoteFolder("Drafts", ".", true),
                new RemoteFolder("Archive.Sent", ".", true),
                new RemoteFolder("Sent", ".", true),
                new RemoteFolder("Junk", ".", true),
                new RemoteFolder("Trash", ".", true)));

        service.syncFromServer(account);

        assertEquals("Sent", sent.getImapName());
    }

    /**
     * 别名与默认名并存时必须认领默认名：用户很可能自己建了一个叫 Spam 的普通文件夹
     */
    @Test
    @DisplayName("Spam 与 Junk 并存时认领 Junk")
    void shouldPreferDefaultNameOverAlias() {
        List<MailFolder> local = systemFolders();
        MailFolder junk = local.get(3);
        junk.setImapName("JUNK_LEGACY");
        when(folderMapper.selectList(any())).thenReturn(local);
        when(imapSupport.listFolders(account)).thenReturn(List.of(
                new RemoteFolder("INBOX", null, true),
                new RemoteFolder("Drafts", null, true),
                new RemoteFolder("Sent", null, true),
                new RemoteFolder("Spam", null, true),
                new RemoteFolder("Junk", null, true),
                new RemoteFolder("Trash", null, true)));

        service.syncFromServer(account);

        assertEquals("Junk", junk.getImapName());
    }

    /**
     * 构造五个系统文件夹
     *
     * @return 系统文件夹列表
     */
    private static List<MailFolder> systemFolders() {
        List<MailFolder> folders = new ArrayList<>();
        folders.add(folder(1L, "INBOX", "SYSTEM", "INBOX", 0));
        folders.add(folder(2L, "DRAFT", "SYSTEM", "Drafts", 10));
        folders.add(folder(3L, "SENT", "SYSTEM", "Sent", 20));
        folders.add(folder(4L, "JUNK", "SYSTEM", "Junk", 30));
        folders.add(folder(5L, "TRASH", "SYSTEM", "Trash", 40));
        return folders;
    }

    /**
     * 构造文件夹实体
     *
     * @param id 主键
     * @param code 编码
     * @param type 类型
     * @param imapName IMAP 名称
     * @param sortNo 排序号
     * @return 实体
     */
    private static MailFolder folder(long id, String code, String type, String imapName, int sortNo) {
        MailFolder folder = new MailFolder();
        folder.setId(id);
        folder.setAccountId(ACCOUNT_ID);
        folder.setFolderCode(code);
        folder.setFolderType(type);
        folder.setImapName(imapName);
        folder.setName(code);
        folder.setLastUid(0L);
        folder.setSortNo(sortNo);
        return folder;
    }
}
