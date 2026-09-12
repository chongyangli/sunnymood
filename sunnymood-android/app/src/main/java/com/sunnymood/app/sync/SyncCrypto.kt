package com.sunnymood.app.sync

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 同步加密（开发文档 §3.10）：
 * 同步口令 → PBKDF2-HMAC-SHA256（随机 salt）派生 256 位密钥 → AES-256-GCM（每次随机 IV）。
 * 口令与密钥永不上传；密文格式：MAGIC(6) + salt(16) + iv(12) + ciphertext(+tag)。
 */
object SyncCrypto {

    private const val MAGIC = "SMENC1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    /** 迭代次数：真机校准派生耗时约 100~200ms 的折中值 */
    const val PBKDF2_ITERATIONS = 100_000

    private val random = SecureRandom()

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** 加密：返回可上传的密文字节流 */
    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain)
        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + iv + cipherText
    }

    /** 解密：口令错误 / 数据损坏抛 GeneralSecurityException */
    fun decrypt(payload: ByteArray, passphrase: String): ByteArray {
        val magicLen = MAGIC.length
        if (payload.size <= magicLen + SALT_LEN + IV_LEN ||
            !MAGIC.contentEquals(payload.copyOfRange(0, magicLen).toString(Charsets.US_ASCII))
        ) {
            throw GeneralSecurityException("bad payload")
        }
        val salt = payload.copyOfRange(magicLen, magicLen + SALT_LEN)
        val iv = payload.copyOfRange(magicLen + SALT_LEN, magicLen + SALT_LEN + IV_LEN)
        val cipherText = payload.copyOfRange(magicLen + SALT_LEN + IV_LEN, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }
}

/** 同步链路业务错误（携带可读信息供 UI 展示） */
class SyncException(message: String) : Exception(message)
