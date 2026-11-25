package com.example.drivenext.presentation.auth

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.drivenext.data.auth.AccountRepository
import com.example.drivenext.data.cloud.Supabase
import com.example.drivenext.domain.auth.AuthFormValidator
import com.example.drivenext.presentation.main.MainActivity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SCREEN = "screen"
        const val LOGIN = 0
        const val REGISTER_ONE = 1
        const val REGISTER_TWO = 2
        const val REGISTER_THREE = 3
        const val SUCCESS = 4
        const val HOME = 5
    }

    private val account by lazy { AccountRepository(applicationContext) }
    private val values = mutableMapOf<String, String>()
    private var screen = LOGIN
    private var enteredFromLogin = false
    private var photoTarget = ""
    private var pendingCameraUri: Uri? = null
    private var busy = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            values[photoTarget] = uri.toString()
            render()
        }
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            pendingCameraUri?.let { values[photoTarget] = it.toString() }
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = savedInstanceState?.getInt(EXTRA_SCREEN) ?: intent.getIntExtra(EXTRA_SCREEN, LOGIN)
        enteredFromLogin = savedInstanceState?.getBoolean("entered_from_login") ?: false
        photoTarget = savedInstanceState?.getString("photo_target").orEmpty()
        pendingCameraUri = savedInstanceState?.getString("camera_uri")?.let(Uri::parse)
        savedInstanceState?.getStringArrayList("entries")?.forEach {
            val separator = it.indexOf('=')
            if (separator > 0) values[it.substring(0, separator)] = it.substring(separator + 1)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        render()
        intent.getStringExtra("oauth_code")?.let { code ->
            runRequest {
                account.finishGoogle(code)
                show(HOME)
            }
        }
        intent.getStringExtra("oauth_error")?.let(::message)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(EXTRA_SCREEN, screen)
        outState.putBoolean("entered_from_login", enteredFromLogin)
        outState.putString("photo_target", photoTarget)
        outState.putString("camera_uri", pendingCameraUri?.toString())
        val draft = values.filterKeys { it !in setOf("password", "repeat", "login_password") }
        outState.putStringArrayList("entries", ArrayList(draft.map { "${it.key}=${it.value}" }))
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        if (screen == HOME) {
            if (account.hasSession()) {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
                return
            }
            screen = LOGIN
        }

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val ui = AuthUi(this, values, ::goBack, ::choosePhoto)
        ui.attach()
        when (screen) {
            LOGIN -> LoginScreen(ui).show(::signIn, {
                enteredFromLogin = true
                show(REGISTER_ONE)
            }, ::recover, ::google)
            REGISTER_ONE -> RegistrationScreens(ui, values).first { advance(AuthFormValidator.firstStep(values), REGISTER_TWO) }
            REGISTER_TWO -> RegistrationScreens(ui, values).second { advance(AuthFormValidator.secondStep(values), REGISTER_THREE) }
            REGISTER_THREE -> RegistrationScreens(ui, values).third(::completeRegistration, ::choosePhoto)
            SUCCESS -> CompletionScreens(ui).success { show(HOME) }
        }
    }

    private fun signIn() {
        AuthFormValidator.login(values)?.let { message(it); return }
        runRequest {
            val email = values["login_email"].orEmpty()
            val password = values["login_password"].orEmpty()
            if (account.signIn(email, password)) show(HOME)
            else {
                values["email"] = email
                values["password"] = password
                enteredFromLogin = true
                show(REGISTER_ONE)
                message("Заверши регистрацию и загрузи документы.")
            }
        }
    }

    private fun completeRegistration() {
        AuthFormValidator.thirdStep(values)?.let { message(it); return }
        AuthFormValidator.firstStep(values)?.let { message(it); show(REGISTER_ONE); return }
        AuthFormValidator.secondStep(values)?.let { message(it); show(REGISTER_TWO); return }
        runRequest {
            account.register(values.toMap())
            show(SUCCESS)
        }
    }

    private fun google() = runRequest {
        startActivity(Intent(Intent.ACTION_VIEW, account.googleUrl()))
    }

    private fun recover() {
        val email = values["login_email"].orEmpty().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            message("Введи электронную почту для восстановления.")
            return
        }
        runRequest {
            account.sendRecovery(email)
            val code = EditText(this).apply { hint = "Код из письма"; inputType = InputType.TYPE_CLASS_NUMBER }
            val password = EditText(this).apply {
                hint = "Новый пароль"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            val fields = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(code)
                addView(password)
            }
            val dialog = AlertDialog.Builder(this).setTitle("Восстановление пароля").setView(fields)
                .setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (code.text.isNotBlank() && password.text.length >= 6) runRequest {
                        account.resetPassword(email, code.text.toString().trim(), password.text.toString())
                        dialog.dismiss()
                        message("Пароль обновлён. Войди с новым паролем.")
                    } else message("Введи код и пароль из 6 или более символов.")
                }
            }
            dialog.show()
        }
    }

    private fun runRequest(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        val progress = AlertDialog.Builder(this).setView(android.widget.ProgressBar(this))
            .setMessage("Подождите…").setCancelable(false).create()
        progress.show()
        lifecycleScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Supabase.Error) {
                message(when (error.code) {
                    "invalid_credentials" -> "Неверная электронная почта или пароль."
                    "email_not_confirmed" -> "Подтверди почту по ссылке из письма и повтори действие."
                    else -> error.message.orEmpty()
                })
            } catch (error: Exception) {
                message(error.localizedMessage ?: "Не удалось выполнить запрос. Попробуй снова.")
            } finally {
                progress.dismiss()
                busy = false
            }
        }
    }

    private fun advance(error: String?, destination: Int) {
        if (error != null) message(error) else show(destination)
    }

    private fun show(destination: Int) {
        screen = destination
        render()
    }

    private fun choosePhoto(target: String) {
        photoTarget = target
        AlertDialog.Builder(this).setItems(arrayOf("Выбрать из галереи", "Сделать фото")) { _, option ->
            if (option == 0) {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                val directory = File(cacheDir, "photos").apply { mkdirs() }
                val file = File.createTempFile("image_", ".jpg", directory)
                pendingCameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                takePhoto.launch(pendingCameraUri!!)
            }
        }.show()
    }

    private fun goBack() {
        when (screen) {
            REGISTER_THREE -> show(REGISTER_TWO)
            REGISTER_TWO -> show(REGISTER_ONE)
            REGISTER_ONE -> if (enteredFromLogin) show(LOGIN) else finish()
            else -> finish()
        }
    }

    private fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
}
