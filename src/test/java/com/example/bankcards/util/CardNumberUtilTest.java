package com.example.bankcards.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CardNumberUtilTest {

    private CardNumberUtil cardNumberUtil;

    @BeforeEach
    void setUp() {
        String base64Key = Base64.getEncoder().encodeToString("test-aes-key-for-testing-only-32".getBytes());
        cardNumberUtil = new CardNumberUtil(base64Key);
    }

    @Test
    void generateCardNumber_shouldReturn16Digits() {
        String number = cardNumberUtil.generateCardNumber();
        assertEquals(16, number.length());
        assertTrue(number.matches("\\d{16}"));
    }

    @Test
    void generateCardNumber_shouldPassLuhnCheck() {
        for (int i = 0; i < 10; i++) {
            String number = cardNumberUtil.generateCardNumber();
            assertTrue(isValidLuhn(number), "Generated number failed Luhn check: " + number);
        }
    }

    @Test
    void encryptAndDecrypt_shouldReturnOriginalNumber() {
        String original = "1234567890123456";
        String encrypted = cardNumberUtil.encrypt(original);
        String decrypted = cardNumberUtil.decrypt(encrypted);

        assertNotEquals(original, encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertexts() {
        String number = "1234567890123456";
        String encrypted1 = cardNumberUtil.encrypt(number);
        String encrypted2 = cardNumberUtil.encrypt(number);

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void mask_shouldShowLastFourDigits() {
        String number = "1234567890123456";
        String masked = cardNumberUtil.mask(number);
        assertEquals("**** **** **** 3456", masked);
    }

    @Test
    void mask_shortInput_shouldReturnStars() {
        assertEquals("****", cardNumberUtil.mask("12"));
        assertEquals("****", cardNumberUtil.mask(null));
    }

    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
