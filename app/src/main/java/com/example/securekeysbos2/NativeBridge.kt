package com.example.securekeysbos2

object NativeBridge {
    init { System.loadLibrary("securefiles") }

    external fun encryptFile(inputPath: String, outputPath: String, key: ByteArray): Boolean
    external fun decryptFile(inputPath: String, outputPath: String, key: ByteArray): Boolean
}
