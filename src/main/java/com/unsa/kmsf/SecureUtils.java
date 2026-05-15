package com.unsa.kmsf;

import java.util.Base64;

public class SecureUtils {
    private static final byte[] XOR_KEY = "K3rn3lM3m0ryStr3@mFilter!".getBytes();

    public static String encrypt(String plainText) {
        byte[] plainBytes = plainText.getBytes();
        byte[] xorBytes = new byte[plainBytes.length];
        for (int i = 0; i < plainBytes.length; i++) {
            xorBytes[i] = (byte) (plainBytes[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return Base64.getEncoder().encodeToString(xorBytes);
    }

    public static String decrypt(String encryptedBase64) {
        byte[] xorBytes = Base64.getDecoder().decode(encryptedBase64);
        byte[] plainBytes = new byte[xorBytes.length];
        for (int i = 0; i < xorBytes.length; i++) {
            plainBytes[i] = (byte) (xorBytes[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return new String(plainBytes);
    }
}
