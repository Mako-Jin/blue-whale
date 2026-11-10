package com.bluewhale.contract.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.contract.entity.CtSeal;
import com.bluewhale.contract.esign.FileClient;
import com.bluewhale.contract.esign.PdfStampService;
import com.bluewhale.contract.mapper.CtSealGrantMapper;
import com.bluewhale.contract.mapper.CtSealMapper;
import com.bluewhale.contract.service.SealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 印章原图导出：MIME 识别、未上传图片与文件读取失败的错误语义。
 *
 * @author BlueWhale
 */
class SealServiceImplDownloadTest {

    /** 印章查询 Mapper 桩 */
    private CtSealMapper sealMapper;

    /** whale-file 客户端桩 */
    private FileClient fileClient;

    /** 被测服务 */
    private SealServiceImpl service;

    /**
     * 构建被测服务与桩对象。
     */
    @BeforeEach
    void setUp() {
        sealMapper = mock(CtSealMapper.class);
        fileClient = mock(FileClient.class);
        service = new SealServiceImpl(sealMapper, mock(CtSealGrantMapper.class), fileClient,
                mock(PdfStampService.class));
    }

    /**
     * PNG 原图按 image/png 导出，并返回带扩展名的安全文件名。
     */
    @Test
    void downloadsPngOriginal() {
        sealWithImage(1L, 9L);
        byte[] png = pngBytes();
        when(fileClient.download(9L)).thenReturn(png);

        SealService.SealImage image = service.downloadImage(1L);

        assertArrayEquals(png, image.bytes());
        assertEquals("image/png", image.contentType());
        assertEquals("seal-1.png", image.filename());
    }

    /**
     * JPEG 原图按 image/jpeg 导出。
     */
    @Test
    void downloadsJpegOriginal() {
        sealWithImage(2L, 10L);
        when(fileClient.download(10L)).thenReturn(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        SealService.SealImage image = service.downloadImage(2L);

        assertEquals("image/jpeg", image.contentType());
        assertEquals("seal-2.jpg", image.filename());
    }

    /**
     * 印章未上传图片时拒绝导出，避免把内置默认章当作原图导出。
     */
    @Test
    void rejectsSealWithoutImage() {
        sealWithImage(3L, null);

        BizException exception = assertThrows(BizException.class, () -> service.downloadImage(3L));

        assertTrue(exception.getMessage().contains("未上传图片"));
    }

    /**
     * 文件通道失败时返回可读错误，不泄露内部异常堆栈。
     */
    @Test
    void reportsFileChannelFailure() {
        sealWithImage(4L, 11L);
        when(fileClient.download(11L)).thenThrow(new IllegalStateException("connect refused"));

        BizException exception = assertThrows(BizException.class, () -> service.downloadImage(4L));

        assertTrue(exception.getMessage().contains("印章原图读取失败"));
    }

    /**
     * 构造带图片文件ID的印章记录。
     *
     * @param id          印章ID
     * @param imageFileId 图片文件ID
     */
    private void sealWithImage(Long id, Long imageFileId) {
        CtSeal seal = new CtSeal();
        seal.setId(id);
        seal.setImageFileId(imageFileId);
        when(sealMapper.selectById(id)).thenReturn(seal);
    }

    /**
     * PNG 文件头字节。
     *
     * @return PNG 字节
     */
    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    }
}
