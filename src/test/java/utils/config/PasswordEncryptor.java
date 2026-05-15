package utils.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordEncryptor {

    private static final String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH   = 12;
    private static final int    TAG_BITS    = 128;
    private static final String ENV_KEY     = "SMTP_SECRET_KEY";
    private static final String FALLBACK_KEY = "CinepolisAut0m@2024!SecrtKey#32!";
    private static final String PREFIX      = "ENC:";

    public static String encrypt(String plainText) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] combined   = new byte[IV_LENGTH + ciphertext.length];
        System.arraycopy(iv,         0, combined, 0,         IV_LENGTH);
        System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

        return PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    public static String decrypt(String encryptedValue) throws Exception {
        String b64      = encryptedValue.startsWith(PREFIX)
                          ? encryptedValue.substring(PREFIX.length())
                          : encryptedValue;
        byte[] combined = Base64.getDecoder().decode(b64);

        byte[] iv         = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0,         iv,         0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_BITS, iv));

        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    public static String decryptIfEncrypted(String value) {
        if (value == null || !value.startsWith(PREFIX)) return value;
        try {
            return decrypt(value);
        } catch (Exception e) {
            throw new RuntimeException("Error descifrando valor SMTP encriptado", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKeySpec buildKey() {
        String raw = System.getenv(ENV_KEY);
        if (raw == null || raw.isBlank()) raw = FALLBACK_KEY;

        byte[] src    = raw.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = new byte[32];
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 32));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
