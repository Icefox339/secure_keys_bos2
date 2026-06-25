package com.example.securekeysbos2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var userManager: UserManager
    private lateinit var keyManager: KeyStoreFileKeyManager
    private lateinit var status: TextView
    private lateinit var userSpinner: Spinner
    private var pendingMode = Mode.ENCRYPT

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) authenticateThenProcess(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userManager = UserManager(this)
        keyManager = KeyStoreFileKeyManager(this)
        buildUi()
        refreshUsers()
        if (!userManager.hasPassword()) askPasswordSetup()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 48, 36, 36) }
        root.addView(TextView(this).apply { text = "Secure Keys BOS2"; textSize = 24f })
        userSpinner = Spinner(this)
        root.addView(userSpinner)
        root.addView(Button(this).apply { text = "Добавить пользователя"; setOnClickListener { askAddUser() } })
        root.addView(Button(this).apply { text = "Изменить пароль"; setOnClickListener { askChangePassword() } })
        root.addView(Button(this).apply { text = "Выбрать файл и зашифровать"; setOnClickListener { openFile(Mode.ENCRYPT) } })
        root.addView(Button(this).apply { text = "Выбрать файл и расшифровать"; setOnClickListener { openFile(Mode.DECRYPT) } })
        status = TextView(this).apply { text = "Файлы обрабатываются нативной C++ библиотекой; ключи файлов защищены Android Keystore."; textSize = 16f }
        root.addView(status)
        setContentView(root)
    }

    private fun refreshUsers() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userManager.users())
        userSpinner.adapter = adapter
        userSpinner.setSelection(userManager.users().indexOf(userManager.currentUser).coerceAtLeast(0))
        userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = userManager.selectUser(adapter.getItem(position)!!)
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun openFile(mode: Mode) {
        pendingMode = mode
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" })
    }

    private fun authenticateThenProcess(uri: Uri) {
        if (canUseBiometrics()) showBiometric(uri) else askPassword { processFile(uri) }
    }

    private fun showBiometric(uri: Uri) {
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = processFile(uri)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = askPassword { processFile(uri) }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Вход ${userManager.currentUser}").setSubtitle("Подтвердите личность биометрией или паролем").setNegativeButtonText("Пароль").build())
    }

    private fun canUseBiometrics(): Boolean = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun askPassword(onOk: () -> Unit) {
        val input = EditText(this).apply { hint = "Пароль"; inputType = 0x00000081 }
        AlertDialog.Builder(this).setTitle("Пароль пользователя ${userManager.currentUser}").setView(input).setPositiveButton("OK") { _, _ ->
            if (userManager.verify(input.text.toString())) onOk() else toast("Неверный пароль")
        }.setNegativeButton("Отмена", null).show()
    }

    private fun askPasswordSetup() = askNewPassword("Задайте пароль") { userManager.setPassword(password = it); toast("Пароль задан") }

    private fun askChangePassword() = askPassword { askNewPassword("Новый пароль") { userManager.setPassword(password = it); toast("Пароль изменён") } }

    private fun askAddUser() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = EditText(this).apply { hint = "Имя пользователя" }
        val pass = EditText(this).apply { hint = "Пароль"; inputType = 0x00000081 }
        box.addView(name); box.addView(pass)
        AlertDialog.Builder(this).setTitle("Новый пользователь").setView(box).setPositiveButton("Создать") { _, _ ->
            userManager.addUser(name.text.toString(), pass.text.toString()); refreshUsers()
        }.setNegativeButton("Отмена", null).show()
    }

    private fun askNewPassword(title: String, onSet: (String) -> Unit) {
        val input = EditText(this).apply { hint = "Минимум 4 символа"; inputType = 0x00000081 }
        AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Сохранить") { _, _ ->
            if (input.text.length >= 4) onSet(input.text.toString()) else toast("Пароль слишком короткий")
        }.show()
    }

    private fun processFile(uri: Uri) {
        val inFile = copyToCache(uri)
        val suffix = if (pendingMode == Mode.ENCRYPT) ".enc" else ".dec"
        val outFile = File(getExternalFilesDir(null), inFile.name + suffix)
        val key = keyManager.getOrCreateFileKey(userManager.currentUser)
        val ok = if (pendingMode == Mode.ENCRYPT) NativeBridge.encryptFile(inFile.path, outFile.path, key) else NativeBridge.decryptFile(inFile.path, outFile.path, key)
        status.text = if (ok) "Готово: ${outFile.path}" else "Ошибка обработки файла"
    }

    private fun copyToCache(uri: Uri): File {
        val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && idx >= 0) c.getString(idx) else "input.bin"
        } ?: "input.bin"
        val target = File(cacheDir, name)
        contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private enum class Mode { ENCRYPT, DECRYPT }
}
