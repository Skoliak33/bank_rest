package com.example.bankcards.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Утилита для работы с номерами банковских карт:
 * - Генерация 16-значного номера с проверкой по алгоритму Луна
 * - Шифрование/расшифровка AES-256-GCM (IV + ciphertext в base64)
 * - Маскирование: **** **** **** 1234
 */
@Component
public class CardNumberUtil {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;      // 96 бит - стандартный размер IV для GCM
    private static final int GCM_TAG_LENGTH = 128;     // 128 бит - тег аутентификации

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CardNumberUtil(@Value("${app.encryption.aes-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        // Берём первые 16 байт (AES-128) или 32 байта (AES-256) в зависимости от длины ключа
        int keyLen = Math.min(keyBytes.length, 32);
        if (keyLen < 16) {
            byte[] padded = new byte[16];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
            keyLen = 16;
        }
        byte[] finalKey = new byte[keyLen >= 32 ? 32 : 16];
        System.arraycopy(keyBytes, 0, finalKey, 0, finalKey.length);
        this.secretKey = new SecretKeySpec(finalKey, "AES");
    }

    /** Генерирует 16-значный номер карты, проходящий проверку по алгоритму Луна. */
    public String generateCardNumber() {
        int[] digits = new int[16];

        // Генерируем первые 15 случайных цифр
        for (int i = 0; i < 15; i++) {
            digits[i] = secureRandom.nextInt(10);
        }

        // Вычисляем контрольную цифру по алгоритму Луна (16-я цифра)
        int sum = 0;
        for (int i = 14; i >= 0; i--) {
            int digit = digits[i];
            if ((15 - i) % 2 == 1) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        digits[15] = (10 - (sum % 10)) % 10;

        StringBuilder sb = new StringBuilder();
        for (int d : digits) sb.append(d);
        return sb.toString();
    }

    /** Шифрует номер карты AES-GCM. Каждый вызов даёт разный результат (случайный IV). */
    public String encrypt(String plainNumber) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] encrypted = cipher.doFinal(plainNumber.getBytes());

            // IV записываем перед шифротекстом - нужен для расшифровки
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Card number encryption failed", e);
        }
    }

    /** Расшифровывает номер карты из base64(IV + ciphertext). */
    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Card number decryption failed", e);
        }
    }

    /** Маскирует номер карты: 1234567890123456 => **** **** **** 3456 */
    public String mask(String plainNumber) {
        if (plainNumber == null || plainNumber.length() < 4) {
            return "****";
        }
        String last4 = plainNumber.substring(plainNumber.length() - 4);
        return "**** **** **** " + last4;
    }
}
