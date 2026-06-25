#include <jni.h>
#include <fstream>
#include <vector>
#include <cstdint>
#include <string>

static uint64_t fnv1a(const std::vector<uint8_t>& key, uint64_t counter) {
    uint64_t h = 1469598103934665603ULL ^ counter;
    for (uint8_t b : key) { h ^= b; h *= 1099511628211ULL; }
    h ^= counter >> 32; h *= 1099511628211ULL;
    return h;
}

static uint8_t nextByte(uint64_t& x) {
    x ^= x << 13; x ^= x >> 7; x ^= x << 17;
    return static_cast<uint8_t>(x & 0xff);
}

static bool transform(const char* inPath, const char* outPath, const std::vector<uint8_t>& key) {
    if (key.empty()) return false;
    std::ifstream in(inPath, std::ios::binary);
    std::ofstream out(outPath, std::ios::binary | std::ios::trunc);
    if (!in || !out) return false;
    std::vector<char> buffer(64 * 1024);
    uint64_t block = 0;
    while (in) {
        in.read(buffer.data(), static_cast<std::streamsize>(buffer.size()));
        const std::streamsize got = in.gcount();
        for (std::streamsize i = 0; i < got; ++i) {
            if ((i % 8) == 0) ++block;
            uint64_t state = fnv1a(key, block);
            for (int skip = 0; skip < (i % 8); ++skip) nextByte(state);
            buffer[static_cast<size_t>(i)] = static_cast<char>(static_cast<uint8_t>(buffer[static_cast<size_t>(i)]) ^ nextByte(state));
        }
        out.write(buffer.data(), got);
    }
    return out.good();
}

static std::vector<uint8_t> jKey(JNIEnv* env, jbyteArray keyArray) {
    const jsize len = env->GetArrayLength(keyArray);
    std::vector<uint8_t> key(static_cast<size_t>(len));
    env->GetByteArrayRegion(keyArray, 0, len, reinterpret_cast<jbyte*>(key.data()));
    return key;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_securekeysbos2_NativeBridge_encryptFile(JNIEnv* env, jobject, jstring inPath, jstring outPath, jbyteArray keyArray) {
    const char* in = env->GetStringUTFChars(inPath, nullptr);
    const char* out = env->GetStringUTFChars(outPath, nullptr);
    bool ok = transform(in, out, jKey(env, keyArray));
    env->ReleaseStringUTFChars(inPath, in);
    env->ReleaseStringUTFChars(outPath, out);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_securekeysbos2_NativeBridge_decryptFile(JNIEnv* env, jobject thiz, jstring inPath, jstring outPath, jbyteArray keyArray) {
    return Java_com_example_securekeysbos2_NativeBridge_encryptFile(env, thiz, inPath, outPath, keyArray);
}
