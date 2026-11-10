package com.bluewhale.contract.esign;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

/**
 * 本地数字签名服务（BouncyCastle CMS/PKCS#7 detached）。
 *
 * <p>未配置 PKCS#12 时直接返回原文件（{@code algorithm=NONE}），仅保留视觉印章与哈希存证；
 * 配置证书后按 PDF 标准签名域（{@code adbe.pkcs7.detached}）追加 CMS 签名：CMS 由 PDFBox
 * 按字节范围（byte range）计算，签名后的最终 PDF 可直接被 PDF 阅读器/验签工具校验。</p>
 *
 * <p>历史版本曾把 CMS 作为 PDF 附件写入，附件写入会改变文件字节，导致签名值与最终文件不再
 * 对应；{@link #verify} 保留该格式的结构化兼容读取，但新签署一律使用标准签名域。</p>
 *
 * @author BlueWhale
 */
@Component
public class SignatureService {

    /** RSA 签名算法 */
    private static final String ALGORITHM_RSA = "SHA256withRSA";

    /** EC 签名算法 */
    private static final String ALGORITHM_EC = "SHA256withECDSA";

    /** 未启用数字签名时的算法标识 */
    private static final String ALGORITHM_NONE = "NONE";

    /** BouncyCastle Provider 名称 */
    private static final String BC_PROVIDER = "BC";

    /** 历史版本 PDF 内签名附件名（仅用于兼容读取） */
    private static final String LEGACY_SIGNATURE_NAME = "signature.p7s";

    /** 签名原因 */
    private static final String SIGN_REASON = "BlueWhale contract signing";

    private final ContractEsignProperties properties;

    /** CA 证书链校验器 */
    private final CertificateChainValidator chainValidator;

    /** RFC 3161 时间戳服务 */
    private final TimestampService timestampService;

    /**
     * 按签章配置构建服务；首次使用时注册 BouncyCastle Provider。
     *
     * @param properties 签章配置
     */
    public SignatureService(ContractEsignProperties properties) {
        this.properties = properties;
        this.chainValidator = new CertificateChainValidator(properties);
        this.timestampService = new TimestampService(properties);
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
     * 对盖章后的 PDF 追加标准数字签名域。
     *
     * <p>CMS 由 PDFBox 基于签名域的字节范围生成，写入签名值不会破坏被签内容，
     * 因此最终文件可用任意 PDF 签名校验工具验签。</p>
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
            chainValidator.validate(material.certificate(), material.chain());
            String algorithm = resolveAlgorithm(material.privateKey());
            try (PDDocument document = PDDocument.load(pdfBytes);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                PDSignature signature = new PDSignature();
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
                signature.setName(material.subject());
                signature.setReason(SIGN_REASON);
                signature.setSignDate(Calendar.getInstance());
                document.addSignature(signature, content -> buildCms(content, material, algorithm));
                document.saveIncremental(output);
                return new SignatureOutcome(output.toByteArray(), material.certSerial(), algorithm);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("数字签名失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 校验 PDF 签名域（优先）或历史附件签名（兼容）。
     *
     * <p>签名域校验包含：CMS 签名值密码学验证、证书序列号匹配、证书在签署时有效。
     * 任一 PDF 字节被修改都会导致被签内容与 messageDigest 不一致而验签失败。</p>
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
            List<PDSignature> signatures = document.getSignatureDictionaries();
            if (!signatures.isEmpty()) {
                return verifySignatureDictionary(signatures.get(signatures.size() - 1), pdfBytes,
                        expectedCertSerial);
            }
            return verifyLegacyAttachment(document, expectedCertSerial);
        } catch (Exception ex) {
            return new VerifyOutcome(false, "CMS 签名解析失败: " + ex.getMessage());
        }
    }

    /**
     * 校验标准 PDF 签名域。
     *
     * @param signature          PDF 签名
     * @param pdfBytes           PDF 字节
     * @param expectedCertSerial 期望证书序列号
     * @return 校验结果
     * @throws Exception 解析异常
     */
    private VerifyOutcome verifySignatureDictionary(PDSignature signature, byte[] pdfBytes,
                                                    String expectedCertSerial) throws Exception {
        byte[] cmsBytes = signature.getContents(pdfBytes);
        byte[] signedContent = signature.getSignedContent(pdfBytes);
        if (cmsBytes == null || cmsBytes.length == 0 || signedContent == null || signedContent.length == 0) {
            return new VerifyOutcome(false, "PDF 签名域缺少签名值或被签内容");
        }
        VerifyOutcome outcome = verifyCms(new CMSProcessableByteArray(signedContent), cmsBytes,
                expectedCertSerial, signature.getSignDate());
        if (!outcome.valid()) {
            return outcome;
        }
        return new VerifyOutcome(true, "PDF 签名域校验通过（" + outcome.detail() + "）");
    }

    /**
     * 兼容校验历史版本写入 PDF 附件的 CMS。
     *
     * <p>历史附件签名的被签内容未随最终文件保存，无法做密码学内容校验，
     * 仅做结构与证书序列号校验，并在明细中明确该限制。</p>
     *
     * @param document          PDF 文档
     * @param expectedCertSerial 期望证书序列号
     * @return 校验结果
     * @throws Exception 读取异常
     */
    private VerifyOutcome verifyLegacyAttachment(PDDocument document, String expectedCertSerial) throws Exception {
        byte[] cms = extractLegacySignature(document);
        if (cms == null) {
            return new VerifyOutcome(false, "PDF 中未找到签名域或 CMS 签名附件");
        }
        CMSSignedData signedData = new CMSSignedData(cms);
        Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
        if (signers.isEmpty()) {
            return new VerifyOutcome(false, "CMS 签名不含签名者信息");
        }
        SignerInformation signer = signers.iterator().next();
        Collection<X509CertificateHolder> matches = signedData.getCertificates().getMatches(signer.getSID());
        if (matches.isEmpty()) {
            return new VerifyOutcome(false, "CMS 签名缺少匹配证书");
        }
        String actualSerial = matches.iterator().next().getSerialNumber().toString(16).toUpperCase();
        if (!actualSerial.equals(expectedCertSerial)) {
            return new VerifyOutcome(false, "证书序列号不一致: 记录=" + expectedCertSerial + ", 实际=" + actualSerial);
        }
        return new VerifyOutcome(true, "历史附件签名结构校验通过（无法校验最终文件内容，建议重新签署）");
    }

    /**
     * 生成 CMS 分离签名（包含签名证书与签名时间属性）。
     *
     * @param content   被签内容
     * @param material  密钥材料
     * @param algorithm 签名算法
     * @return CMS 编码
     * @throws IOException 签名异常
     */
    private byte[] buildCms(InputStream content, KeyMaterial material, String algorithm) throws IOException {
        try {
            byte[] signedBytes = content.readAllBytes();
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            ContentSigner contentSigner = new JcaContentSignerBuilder(algorithm)
                    .setProvider(BC_PROVIDER)
                    .build(material.privateKey());
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().setProvider(BC_PROVIDER).build())
                    .build(contentSigner, material.certificate()));
            List<X509Certificate> certificates = material.chain().isEmpty()
                    ? List.of(material.certificate()) : material.chain();
            generator.addCertificates(new JcaCertStore(certificates));
            CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(signedBytes), true);
            return applyTimestamp(signedData);
        } catch (Exception ex) {
            throw new IOException("生成 CMS 分离签名失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 为 CMS 签名值追加 RFC 3161 时间戳（未配置 TSA 时原样返回）。
     *
     * @param signedData CMS 签名
     * @return 含时间戳属性的 CMS 编码
     * @throws IOException 时间戳或编码异常
     */
    private byte[] applyTimestamp(CMSSignedData signedData) throws IOException {
        if (!timestampService.isEnabled()) {
            return signedData.getEncoded();
        }
        try {
            Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                throw new IllegalStateException("CMS 无签名者，无法申请时间戳");
            }
            SignerInformation signer = signers.iterator().next();
            byte[] token = timestampService.timestamp(signer.getSignature());
            ASN1EncodableVector unsignedAttributes = new ASN1EncodableVector();
            unsignedAttributes.add(new Attribute(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                    new DERSet(ASN1Primitive.fromByteArray(token))));
            SignerInformation timestamped = SignerInformation.replaceUnsignedAttributes(
                    signer, new AttributeTable(unsignedAttributes));
            CMSSignedData updated = CMSSignedData.replaceSigners(signedData,
                    new SignerInformationStore(List.of(timestamped)));
            return updated.getEncoded();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("RFC 3161 时间戳写入 CMS 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 校验 CMS 签名值、证书序列号与证书有效期。
     *
     * @param signedContent      被签内容
     * @param cmsBytes           CMS 编码
     * @param expectedCertSerial 期望证书序列号
     * @param signDate           签署时间（为空时按当前时间校验）
     * @return 校验结果
     * @throws Exception 解析异常
     */
    private VerifyOutcome verifyCms(CMSProcessableByteArray signedContent, byte[] cmsBytes,
                                    String expectedCertSerial, Calendar signDate) throws Exception {
        CMSSignedData signedData = new CMSSignedData(signedContent, cmsBytes);
        Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
        if (signers.isEmpty()) {
            return new VerifyOutcome(false, "CMS 签名不含签名者信息");
        }
        SignerInformation signer = signers.iterator().next();
        Collection<X509CertificateHolder> matches = signedData.getCertificates().getMatches(signer.getSID());
        if (matches.isEmpty()) {
            return new VerifyOutcome(false, "CMS 签名缺少匹配证书");
        }
        X509CertificateHolder holder = matches.iterator().next();
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BC_PROVIDER)
                .getCertificate(holder);
        String actualSerial = certificate.getSerialNumber().toString(16).toUpperCase();
        if (!actualSerial.equals(expectedCertSerial)) {
            return new VerifyOutcome(false, "证书序列号不一致: 记录=" + expectedCertSerial + ", 实际=" + actualSerial);
        }
        boolean signatureValid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(BC_PROVIDER).build(holder));
        if (!signatureValid) {
            return new VerifyOutcome(false, "CMS 签名值与 PDF 被签内容不匹配（文件可能被篡改）");
        }
        certificate.checkValidity(signDate == null ? new java.util.Date() : signDate.getTime());
        String chainDetail = verifyChain(signedData, certificate);
        String timestampDetail = verifyTimestamp(signer);
        return new VerifyOutcome(true, "算法 " + signer.getDigestAlgOID() + "，证书序列号 "
                + actualSerial + chainDetail + timestampDetail);
    }

    /**
     * 校验签名证书链（未配置信任根时仅提示）。
     *
     * @param signedData  CMS 签名
     * @param certificate 签名证书
     * @return 明细说明
     * @throws Exception 证书转换异常
     */
    private String verifyChain(CMSSignedData signedData, X509Certificate certificate) throws Exception {
        if (!chainValidator.isConfigured()) {
            return "，未配置 CA 信任根（仅校验证书本身）";
        }
        List<X509Certificate> candidates = new ArrayList<>();
        for (X509CertificateHolder holder : signedData.getCertificates().getMatches(null)) {
            candidates.add(new JcaX509CertificateConverter().setProvider(BC_PROVIDER)
                    .getCertificate(holder));
        }
        chainValidator.validate(certificate, candidates);
        return "，证书链已链接到配置的 CA 信任根";
    }

    /**
     * 校验 CMS 未签名属性中的 RFC 3161 时间戳。
     *
     * @param signer 签名者信息
     * @return 明细说明
     * @throws IllegalStateException 时间戳缺失或校验失败
     */
    private String verifyTimestamp(SignerInformation signer) {
        AttributeTable unsigned = signer.getUnsignedAttributes();
        ASN1EncodableVector attributes = unsigned == null ? null
                : unsigned.getAll(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
        if (attributes == null || attributes.size() == 0) {
            if (properties.isTsaRequired()) {
                throw new IllegalStateException("签名缺少 RFC 3161 时间戳（tsa-required=true）");
            }
            return "，未包含 RFC 3161 时间戳";
        }
        Attribute attribute = Attribute.getInstance(attributes.get(0));
        try {
            byte[] token = attribute.getAttrValues().getObjectAt(0)
                    .toASN1Primitive().getEncoded();
            return "，" + timestampService.verify(token, signer.getSignature());
        } catch (IOException ex) {
            throw new IllegalStateException("RFC 3161 时间戳解析失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 按私钥类型选择签名算法。
     *
     * @param privateKey 私钥
     * @return 签名算法
     */
    private String resolveAlgorithm(PrivateKey privateKey) {
        return "EC".equalsIgnoreCase(privateKey.getAlgorithm()) ? ALGORITHM_EC : ALGORITHM_RSA;
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
                return new KeyMaterial(privateKey, x509, certificateChain(keyStore, alias, x509),
                        x509.getSerialNumber().toString(16).toUpperCase(),
                        x509.getSubjectX500Principal().getName());
            }
        }
        throw new IllegalStateException("PKCS#12 中未找到可用私钥条目");
    }

    /**
     * 从历史版本 PDF 中提取 CMS 签名附件。
     *
     * @param document PDF 文档
     * @return CMS 字节；不存在时返回 {@code null}
     * @throws Exception 读取异常
     */
    private byte[] extractLegacySignature(PDDocument document) throws Exception {
        PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
        if (names == null) {
            return null;
        }
        PDEmbeddedFilesNameTreeNode tree = names.getEmbeddedFiles();
        if (tree == null || tree.getNames() == null) {
            return null;
        }
        PDComplexFileSpecification specification = tree.getNames().get(LEGACY_SIGNATURE_NAME);
        if (specification == null || specification.getEmbeddedFile() == null) {
            return null;
        }
        PDEmbeddedFile embedded = specification.getEmbeddedFile();
        byte[] bytes = embedded.toByteArray();
        return bytes == null || bytes.length == 0 ? null : bytes;
    }

    /**
     * 读取 PKCS#12 条目对应的证书链（缺失时退化为单证书）。
     *
     * @param keyStore 密钥库
     * @param alias    条目别名
     * @param leaf     签名证书
     * @return 证书链（含签名证书）
     */
    private List<X509Certificate> certificateChain(KeyStore keyStore, String alias, X509Certificate leaf)
            throws java.security.KeyStoreException {
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            return List.of(leaf);
        }
        List<X509Certificate> certificates = new ArrayList<>(chain.length);
        for (Certificate item : chain) {
            if (item instanceof X509Certificate x509) {
                certificates.add(x509);
            }
        }
        return certificates.isEmpty() ? List.of(leaf) : certificates;
    }

    /**
     * 密钥材料。
     *
     * @param privateKey  私钥
     * @param certificate X.509 证书
     * @param chain       证书链（含签名证书）
     * @param certSerial  证书序列号（十六进制大写）
     * @param subject     证书主题
     */
    private record KeyMaterial(PrivateKey privateKey, X509Certificate certificate,
                               List<X509Certificate> chain, String certSerial, String subject) {
    }
}
