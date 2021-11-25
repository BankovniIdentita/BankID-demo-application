package cz.bankid.demo.utils

import java.security.SecureRandom
import java.util.*

class CustomStringUtils {
    companion object {
        fun getRandomString(): String {
            val byteLength = 16
            val secureRandom = SecureRandom()
            val token = ByteArray(byteLength)
            secureRandom.nextBytes(token)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token)
        }
    }
}