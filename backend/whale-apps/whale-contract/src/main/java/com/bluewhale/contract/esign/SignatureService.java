package com.bluewhale.contract.esign;

import com.bluewhale.contract.config.ContractEsignProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地数字签名服务（BouncyCastle CMS/PKCS#7 detached）。
 *
 * <p>未配置 PKCS#12 时直接返回原文件（{@code algorithm=NONE}），仅保留视觉印章与哈希存证；
 * 配置证书后对盖章后的 PDF 计算 detached CMS 签名，并以附件形式写入 PDF，
 * 同时把签名证书序列号写入签署记录。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class SignatureService {

    /** 签名算法 */
    private static final String ALGORITHM = "SHA256withRSA";

    /** 未启用数字签名时的算法标识 */
    private static final String ALGORITHM_NONE = "NONE";

    /** BouncyCastle Provider 名称 */
    private static final String BC_PROVIDER = "BC";

    /** PDF 内签名附件名 */
    private static final String SIGNATURE_NAME = "signature.p7s";

    /** 签名附件 MIME 子类型 */
    private static final String SIGNATURE_SUBTYPE = "application/pkcs7-signature";

    private final ContractEsignProperties properties;

    /**
     * 按签章配置构建服务；首次使用时注册 BouncyCastle Provider。
     *
     * @param properties 签章配置
     */
    public SignatureService(ContractEsignProperties properties) {
        this.properties = properties;
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 签名结果。
     *
     * @param bytes     最终文件字节
     * @param certSerial 签名证书序列号（未启用时为空）
     * @param algorithm 算法标识
     */
    public record SignatureOutcome(byte[] bytes, String certSerial, String algorithm) {
    }

    /**
     * 验证结果。
     *
     * @param valid  是否通过
     * @param detail 明细说明
     */
    public record VerifyOutcome(boolean valid, String detail) {
    }

    /**
     * 对盖章后的 PDF 追加数字签名。
     *
     * @param pdfBytes 盖章后的 PDF
     * @return 签名结果
     */
    public SignatureOutcome sign(byte[] pdfBytes) {
        if (!properties.isDigitalSignatureEnabled()) {
            return new SignatureOutcome(pdfBytes, null, ALGORITHM_NONE);
        }
        try {
            KeyMaterial material = loadKeyMaterial();
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            ContentSigner contentSigner = new JcaContentSignerBuilder(ALGORITHM)
                    .setProvider(BC_PROVIDER)
                    .build(material.privateKey());
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().setProvider(BC_PROVIDER).build())
                    .build(contentSigner, material.certificate()));
            generator.addCertificates(new org.bouncycastle.cert.jcajce.JcaCertStore(List.of(material.certificate())));
            CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(pdfBytes), true);
            byte[] finalBytes = embedSignature(pdfBytes, signedData.getEncoded());
            return new SignatureOutcome(finalBytes, material.certSerial(), ALGORITHM);
        } catch (Exception ex) {
            throw new IllegalStateException("数字签名失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 结构校验 PDF 内的 CMS 签名（证书序列号匹配）。
     *
     * @param pdfBytes          最终签章件
     * @param expectedCertSerial 记录中的证书序列号
     * @return 校验结果
     */
    public VerifyOutcome verify(byte[] pdfBytes, String expectedCertSerial) {
        if (expectedCertSerial == null || expectedCertSerial.isBlank()) {
            return new VerifyOutcome(true, "未启用数字签名（仅视觉章 + 哈希存证）");
        }
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            byte[] cms = extractSignature(document);
            if (cms == null) {
                return new VerifyOutcome(false, "PDF 中未找到 CMS 签名附件");
            }
            CMSSignedData signedData = new CMSSignedData(cms);
            Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                return new VerifyOutcome(false, "CMS 签名不含签名者信息");
            }
            SignerInformation signer = signers.iterator().next();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = signedData.getCertificates().getMatches(signer.getSID());
            if (matches.isEmpty()) {
                return new VerifyOutcome(false, "CMS 签名缺少匹配证书");
            }
            String actualSerial = matches.iterator().next().getSerialNumber().toString(16).toUpperCase();
            if (!actualSerial.equals(expectedCertSerial)) {
                return new VerifyOutcome(false, "证书序列号不一致: 记录=" + expectedCertSerial + ", 实际=" + actualSerial);
            }
            return new VerifyOutcome(true, "CMS 签名结构校验通过（算法 " + ALGORITHM + "）");
        } catch (Exception ex) {
            return new VerifyOutcome(false, "CMS 签名解析失败: " + ex.getMessage());
        }
    }

    /**
     * 加载 PKCS#12 密钥材料（取首个含私钥的条目）。
     *
     * @return 密钥材料
     * @throws Exception 读取异常
     */
    private KeyMaterial loadKeyMaterial() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = properties.getP12Password() == null ? new char[0]
                : properties.getP12Password().toCharArray();
        try (InputStream input = Files.newInputStream(Path.of(properties.getP12Path()))) {
            keyStore.load(input, password);
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            Certificate certificate = keyStore.getCertificate(alias);
            if (privateKey != null && certificate instanceof X509Certificate x509) {
                return new KeyMaterial(privateKey, x509,
                        x509.getSerialNumber().toString(16).toUpperCase());
            }
        }
        throw new IllegalStateException("PKCS#12 中未找到可用私钥条目");
    }

    /**
     * 把 CMS 签名作为附件写入 PDF。
     *
     * @param pdfBytes PDF 字节
     * @param cms      CMS 签名
     * @return 追加附件后的 PDF 字节
     * @throws Exception 写入异常
     */
    private byte[] embedSignature(byte[] pdfBytes, byte[] cms) throws Exception {
        try (PDDocument document = PDDocument.load(pdfBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDEmbeddedFile embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(cms));
            embedded.setSubtype(SIGNATURE_SUBTYPE);
            embedded.setSize(cms.length);
            PDComplexFileSpecification specification = new PDComplexFileSpecification();
            specification.setFile(SIGNATURE_NAME);
            specification.setEmbeddedFile(embedded);

            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDDocumentNameDictionary names = catalog.getNames();
            if (names == null) {
                names = new PDDocumentNameDictionary(catalog);
                catalog.setNames(names);
            }
            PDEmbeddedFilesNameTreeNode tree = names.getEmbeddedFiles();
            if (tree == null) {
                tree = new PDEmbeddedFilesNameTreeNode();
                names.setEmbeddedFiles(tree);
            }
            Map<String, PDComplexFileSpecification> entries = tree.getNames() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(tree.getNames());
            entries.put(SIGNATURE_NAME, specification);
            tree.setNames(entries);
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * 从 PDF 中提取 CMS 签名附件。
     *
     * @param document PDF 文档
     * @return CMS 字节；不存在时返回 {@code null}
     * @throws Exception 读取异常
     */
    private byte[] extractSignature(PDDocument document) throws Exception {
        PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
        if (names == null) {
            return null;
        }
        PDEmbeddedFilesNameTreeNode tree = names.getEmbeddedFiles();
        if (tree == null || tree.getNames() == null) {
            return null;
        }
        PDComplexFileSpecification specification = tree.getNames().get(SIGNATURE_NAME);
        if (specification == null || specification.getEmbeddedFile() == null) {
            return null;
        }
        PDEmbeddedFile embedded = specification.getEmbeddedFile();
        byte[] bytes = embedded.toByteArray();
        return bytes == null || bytes.length == 0 ? null : bytes;
    }

    /**
     * 密钥材料。
     *
     * @param privateKey 私钥
     * @param certificate X.509 证书
     * @param certSerial 证书序列号（十六进制大写）
     */
    private record KeyMaterial(PrivateKey privateKey, X509Certificate certificate, String certSerial) {
    }
}
