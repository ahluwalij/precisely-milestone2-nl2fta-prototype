package com.nl2fta.classifier.config;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RsaCryptoService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RsaCryptoService.class);

  private KeyPair keyPair;

  @PostConstruct
  public void init() {
    try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
      keyGen.initialize(2048);
      this.keyPair = keyGen.generateKeyPair();
      LOGGER.info("RSA key pair generated for secure credential transport.");
    } catch (Exception e) {
      LOGGER.error("Failed to initialize RSA key pair", e);
      throw new IllegalStateException("RSA initialization failed", e);
    }
  }

  public String getPublicKeyPem() {
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
    StringBuilder pem = new StringBuilder();
    pem.append("-----BEGIN PUBLIC KEY-----\n");
    for (int i = 0; i < base64.length(); i += 64) {
      pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
    }
    pem.append("-----END PUBLIC KEY-----\n");
    return pem.toString();
  }

  public String decryptBase64(String base64CipherText) {
    try {
      byte[] cipherBytes = Base64.getDecoder().decode(base64CipherText);
      Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
      PrivateKey privateKey = keyPair.getPrivate();
      OAEPParameterSpec oaepParams =
          new OAEPParameterSpec(
              "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
      cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
      byte[] plain = cipher.doFinal(cipherBytes);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOGGER.error("Failed to decrypt credential payload", e);
      throw new IllegalArgumentException("Invalid encrypted payload", e);
    }
  }

  // Utility to import PEM public key if ever needed elsewhere (not used server-side now)
  public static PublicKey parsePublicKeyPem(String pem) {
    try {
      String normalized =
          pem.replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s", "");
      byte[] decoded = Base64.getDecoder().decode(normalized);
      X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
      return KeyFactory.getInstance("RSA").generatePublic(spec);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid PEM public key", e);
    }
  }
}
