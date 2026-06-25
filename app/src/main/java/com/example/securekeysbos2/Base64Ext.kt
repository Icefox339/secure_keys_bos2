package com.example.securekeysbos2

fun ByteArray.toB64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
fun String.fromB64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
