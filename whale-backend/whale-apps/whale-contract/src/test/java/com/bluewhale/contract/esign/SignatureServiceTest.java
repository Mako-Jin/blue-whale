package com.bluewhale.contract.esign;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地 PKCS#12 数字签名测试：签署、验签、篡改检测与未配置证书的降级行为。
 *
 * @author BlueWhale
 */
class SignatureServiceTest {

    /** 测试证书口令 */
    private static final char[] PASSWORD = "changeit".toCharArray();

    /** 签名算法 */
    private static final String ALGORITHM = "SHA256withRSA";

    /** 测试 PDF 的文件头前缀 */
    private static final String PDF_HEADER_PREFIX = "%PDF-";

    /** 临时目录中的证书路径 */
    @TempDir
    Path tempDir;

    /**
     * 注册 BouncyCastle Provider，确保证书与 CMS 构建可用。
     */
    @BeforeAll
    static void registerProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 配置证书后可签署，且标准 PDF 签名域可被密码学历验签。
     *
     * @throws Exception 证书/签名异常
     */
    @Test
    void signsAndVerifiesWithP12() throws Exception {
        SignatureService service = new SignatureService(propertiesWithP12());

        SignatureService.SignatureOutcome outcome = service.sign(samplePdf());

        assertEquals(ALGORITHM, outcome.algorithm());
        assertNotNull(outcome.certSerial());
        assertTrue(new String(outcome.bytes(), 0, 4, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        SignatureService.VerifyOutcome verify = service.verify(outcome.bytes(), outcome.certSerial());
        assertTrue(verify.valid(), verify.detail());
        assertTrue(verify.detail().contains("PDF 签名域校验通过"), verify.detail());
    }

    /**
     * 修改签名后的 PDF 正文，CMS 与被签内容不再匹配，验签必须失败。
     *
     * @throws Exception 证书/签名异常
     */
    @Test
    void detectsContentTampering() throws Exception {
        SignatureService service = new SignatureService(propertiesWithP12());
        SignatureService.SignatureOutcome outcome = service.sign(samplePdf());

        byte[] tampered = tamperVersion(outcome.bytes());
        SignatureService.VerifyOutcome verify = service.verify(tampered, outcome.certSerial());

        assertFalse(verify.valid(), verify.detail());
    }

    /**
     * 记录中的证书序列号与实际签名证书不一致时必须失败。
     *
     * @throws Exception 证书/签名异常
     */
    @Test
    void detectsCertificateSerialMismatch() throws Exception {
        SignatureService service = new SignatureService(propertiesWithP12());
        SignatureService.SignatureOutcome outcome = service.sign(samplePdf());

        SignatureService.VerifyOutcome verify = service.verify(outcome.bytes(), "DEADBEEF");
        assertFalse(verify.valid(), verify.detail());
        assertTrue(verify.detail().contains("证书序列号不一致"), verify.detail());
    }

    /**
     * 未配置证书时保持原文件并返回 NONE，验签按仅视觉章通过。
     *
     * @throws Exception PDF 异常
     */
    @Test
    void returnsNoneWithoutP12() throws Exception {
        SignatureService service = new SignatureService(new ContractEsignProperties());
        byte[] pdf = samplePdf();

        SignatureService.SignatureOutcome outcome = service.sign(pdf);

        assertArrayEquals(pdf, outcome.bytes());
        assertEquals("NONE", outcome.algorithm());
        assertTrue(service.verify(outcome.bytes(), null).valid());
    }

    /**
     * PKCS#12 口令错误时签署失败，避免静默退回视觉章。
     *
     * @throws Exception 证书异常
     */
    @Test
    void rejectsWrongP12Password() throws Exception {
        ContractEsignProperties properties = propertiesWithP12();
        properties.setP12Password("wrong-password");
        SignatureService service = new SignatureService(properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.sign(samplePdf()));
        assertTrue(exception.getMessage().contains("数字签名失败"), exception.getMessage());
    }

    /**
     * 生成包含一组 RSA 密钥与自签证书的 PKCS#12 配置。
     *
     * @return 签章配置
     * @throws Exception 证书生成异常
     */
    private ContractEsignProperties propertiesWithP12() throws Exception {
        Path p12 = tempDir.resolve("bluewhale-test.p12");
        if (!Files.exists(p12)) {
            createP12(p12);
        }
        ContractEsignProperties properties = new ContractEsignProperties();
        properties.setP12Path(p12.toString());
        properties.setP12Password(new String(PASSWORD));
        return properties;
    }

    /**
     * 生成 RSA 密钥对、自签 X.509 证书并写入 PKCS#12。
     *
     * @param target 目标文件
     * @throws Exception 生成异常
     */
    private void createP12(Path target) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=BlueWhale Test Signer, O=BlueWhale");
        Date notBefore = new Date(System.currentTimeMillis() - 86_400_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 86_400_000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject,
                BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter, subject,
                keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(ALGORITHM).setProvider("BC")
                .build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(signer));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("signer", keyPair.getPrivate(), PASSWORD, new Certificate[]{certificate});
        try (OutputStream output = Files.newOutputStream(target)) {
            keyStore.store(output, PASSWORD);
        }
    }

    /**
     * 生成一页包含可定位文本的测试 PDF。
     *
     * @return PDF 字节
     * @throws Exception 生成异常
     */
    private byte[] samplePdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("contract body");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * 将 PDF 头部版本号的最后一位改为另一个合法版本，PDF 结构仍可解析，
     * 但文件字节已与签名时不同。
     *
     * @param source 原字节
     * @return 篡改后的字节
     */
    private byte[] tamperVersion(byte[] source) {
        byte[] prefix = PDF_HEADER_PREFIX.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = 0; i <= source.length - prefix.length; i++) {
            boolean matched = true;
            for (int j = 0; j < prefix.length; j++) {
                if (source[i + j] != prefix[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                byte[] result = source.clone();
                int versionIndex = i + prefix.length;
                result[versionIndex] = result[versionIndex] == '7' ? (byte) '6' : (byte) '7';
                return result;
            }
        }
        throw new AssertionError("测试 PDF 中未找到文件头: " + PDF_HEADER_PREFIX);
    }
}
