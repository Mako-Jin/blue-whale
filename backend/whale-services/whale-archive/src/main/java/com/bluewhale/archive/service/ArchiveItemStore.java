package com.bluewhale.archive.service;

import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.entity.ArchiveSourceRecord;
import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.mapper.ArchiveSourceRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 档案条目落库服务。
 *
 * <p>独立于同步服务存在，保证 {@code @Transactional} 通过代理生效（同类自调用不触发事务）；
 * 条目、文件引用与同步台账在同一事务内提交，避免出现「有条目无台账」或反之的半成品状态。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ArchiveItemStore {

    private final ArchiveItemMapper itemMapper;

    private final ArchiveFileMapper fileMapper;

    private final ArchiveSourceRecordMapper sourceRecordMapper;

    /**
     * 归档成功落库：条目 + 文件引用 + 同步台账。
     *
     * @param item 档案条目（新增）
     * @param files 文件引用（不含条目ID，落库时回填）
     * @param record 同步台账（新增或更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public void persist(ArchiveItem item, List<ArchiveFile> files, ArchiveSourceRecord record) {
        itemMapper.insert(item);
        for (ArchiveFile file : files) {
            file.setItemId(item.getId());
            fileMapper.insert(file);
        }
        saveRecord(record);
    }

    /**
     * 写入同步台账（新增或更新），供成功/跳过/失败三类结果复用。
     *
     * @param record 同步台账
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRecord(ArchiveSourceRecord record) {
        if (record.getId() == null) {
            sourceRecordMapper.insert(record);
        } else {
            sourceRecordMapper.updateById(record);
        }
    }
}
