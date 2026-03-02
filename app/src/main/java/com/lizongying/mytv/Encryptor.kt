package com.lizongying.mytv

import android.content.Context
import android.util.Log

class Encryptor {
    private var isInitialized = false
    
    external fun init(context: Context): Boolean

    external fun encrypt(t: String, e: String, r: String, n: String, i: String): String

    external fun hash(data: ByteArray): ByteArray?

    external fun hash2(data: ByteArray): ByteArray?

    fun safeInit(context: Context): Boolean {
        if (!isInitialized) {
            try {
                isInitialized = init(context)
            } catch (e: Exception) {
                Log.e("Encryptor", "Initialization failed", e)
                isInitialized = false
            }
        }
        return isInitialized
    }

    companion object {
        init {
            try {
                System.loadLibrary("native")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("Encryptor", "Failed to load native library", e)
            }
        }
    }
}