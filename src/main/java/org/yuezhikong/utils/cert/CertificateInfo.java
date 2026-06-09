package org.yuezhikong.utils.cert;

import lombok.Data;
import org.bouncycastle.asn1.x500.X500Name;

import java.math.BigInteger;
import java.util.Date;

@Data
public class CertificateInfo {
    /**
     * 证书序列号
     */
    private BigInteger serial;
    /**
     * 颁发者
     */
    private X500Name issuer;
    /**
     * 主体
     */
    private X500Name subject;
    /**
     * 颁发时间
     */
    private Date notBefore;
    /**
     * 到期时间
     */
    private Date notAfter;
    /**
     * 加密算法
     */
    private String keyAlgorithm;
    /**
     * 签名算法
     */
    private String signAlgorithm;
}
