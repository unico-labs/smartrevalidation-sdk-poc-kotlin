package com.example.smartrevalidation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.acesso.acessobio_android.*
import com.acesso.acessobio_android.onboarding.AcessoBio
import com.acesso.acessobio_android.onboarding.camera.CameraListener
import com.acesso.acessobio_android.onboarding.camera.UnicoCheckCameraOpener
import com.acesso.acessobio_android.onboarding.camera.document.DocumentCameraListener
import com.acesso.acessobio_android.onboarding.models.Environment
import com.acesso.acessobio_android.onboarding.types.DocumentType
import com.acesso.acessobio_android.services.dto.ErrorBio
import com.acesso.acessobio_android.services.dto.PrepareInfo
import com.acesso.acessobio_android.services.dto.ResultCamera
import com.acesso.acessobio_android.services.dto.SuccessResult
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(),
    AcessoBioListener,
    iAcessoBioSelfie,
    CameraListener,
    iAcessoBioDocument,
    DocumentCameraListener {

    private lateinit var textField: TextView
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var documentType: DocumentType
    private lateinit var externalUserIdInput: EditText
    private lateinit var subjectCodeInput: EditText
    private lateinit var subjectNameInput: EditText
    private lateinit var bearerTokenInput: EditText
    private val unicoTheme = UnicoTheme()
    private val timeout = 50.0
    private val CAMERA_PERMISSION_CODE = 1001
    private val TAG = "MainActivity"

    private var pendingSilentAuthExternalUserId: String? = null
    private var activeSilentAuthUserId: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textField = findViewById(R.id.mainText)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        externalUserIdInput = findViewById(R.id.externalUserIdInput)
        subjectCodeInput = findViewById(R.id.subjectCodeInput)
        subjectNameInput = findViewById(R.id.subjectNameInput)
        bearerTokenInput = findViewById(R.id.bearerTokenInput)

        findViewById<TextView>(R.id.clearLogButton).setOnClickListener {
            logTextView.text = ""
            addLog("Log limpo.")
        }
    }

    private fun addLog(message: String) {
        runOnUiThread {

            logTextView.append("\n$message")
            logScrollView.post {
                logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
        Log.d(TAG, message)
    }



    fun openCameraLiveness(view: View) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCameraLiveness()
        } else {
            addLog("Solicitando permissão de câmera...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun startCameraLiveness() {
        addLog("Permissão de câmera concedida. Iniciando SDK Liveness.")
        try {
            AcessoBio(this, this)
                .setTheme(unicoTheme)
                .setTimeoutSession(timeout)
                .setEnvironment(Environment.UAT)
                .build()
                .prepareCamera(UnicoConfig(), this)
        } catch (e: Exception) {
            addLog("Erro no startCameraLiveness: ${e.message}")
        }
    }

    fun openSilentAuthTest(view: View) {
        val externalUserId = externalUserIdInput.text.toString().trim()
        if (externalUserId.isBlank()) {
            addLog("Erro: informe um externalUserId antes de testar o SilentAuth.")
            Toast.makeText(this, "Informe o externalUserId", Toast.LENGTH_LONG).show()
            return
        }

        addLog("Iniciando teste SilentAuth. externalUserId=$externalUserId")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startSilentAuthCamera(externalUserId)
        } else {
            addLog("Solicitando permissão de câmera para SilentAuth...")
            pendingSilentAuthExternalUserId = externalUserId
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun startSilentAuthCamera(externalUserId: String) {
        activeSilentAuthUserId = externalUserId
        try {
            AcessoBio(this, this)
                .setTheme(unicoTheme)
                .setTimeoutSession(timeout)
                .setEnvironment(Environment.UAT)
                .build()
                .prepareCamera(
                    UnicoConfig(),
                    this,
                    PrepareInfo(externalUserId = externalUserId, useCase = null)
                )
        } catch (e: Exception) {
            activeSilentAuthUserId = null
            addLog("Erro no startSilentAuthCamera: ${e.message}")
        }
    }

    private val apiKey = "YOUR_API_KEY"

    private fun createProcess(externalUserId: String) {
        val subjectCode = subjectCodeInput.text.toString().trim()
        val subjectName = subjectNameInput.text.toString().trim()
        val bearerToken = bearerTokenInput.text.toString().trim()

        if (bearerToken.isBlank()) {
            addLog("Erro: preencha o Bearer Token antes de criar o processo.")
            return
        }

        addLog("Disparando CreateProcess...")

        Thread {
            try {
                val body = JSONObject()
                    .put("subject", JSONObject()
                        .put("code", subjectCode)
                        .put("name", subjectName))
                    .put("externalUserId", externalUserId)

                addLog("POST /processes/v1 body: $body")

                val connection = URL("https://api.id.uat.unico.app/processes/v1")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("APIKEY", apiKey)
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { os ->
                    os.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""

                addLog("CreateProcess -> HTTP $responseCode: $responseBody")
            } catch (e: Exception) {
                addLog("Erro ao chamar CreateProcess: ${e.message}")
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addLog("Permissão da câmera concedida.")
                val silentAuthUserId = pendingSilentAuthExternalUserId
                pendingSilentAuthExternalUserId = null
                if (silentAuthUserId != null) {
                    startSilentAuthCamera(silentAuthUserId)
                } else {
                    startCameraLiveness()
                }
            } else {
                addLog("Permissão da câmera negada pelo usuário.")
                Toast.makeText(this, "Permissão da câmera é obrigatória para continuar.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onErrorAcessoBio(error: ErrorBio?) {
        addLog("Erro AcessoBio: ${error.toString()}")
        textField.text = error.toString()
    }

    override fun onUserClosedCameraManually() {
        addLog("Usuário fechou a câmera manualmente.")
        textField.text = "Camera fechada manualmente."
        activeSilentAuthUserId = null
    }

    override fun onSystemClosedCameraTimeoutSession() {
        addLog("Sessão encerrada por timeout.")
        textField.text = "Tempo de sessão excedido."
        activeSilentAuthUserId = null
    }

    override fun onSystemChangedTypeCameraTimeoutFaceInference() {
        addLog("Timeout de inferência de face.")
        textField.text = "Tempo de inferência excedido."
    }

    override fun onSuccessSelfie(result: ResultCamera) {
        addLog("Selfie capturada com sucesso.")
        textField.text = "Selfie capturada com sucesso."

        addLog("JWT da selfie capturado com sucesso.")

        Log.d(TAG, "JWT COMPLETO DA SELFIE: ${result.encrypted}")
    }

    override fun onErrorSelfie(error: ErrorBio?) {
        addLog("Erro na selfie: ${error.toString()}")
        textField.text = error.toString()
        activeSilentAuthUserId = null
    }

    override fun onSuccess(result: SuccessResult) {
        addLog("Processo finalizado com sucesso.")
        textField.text = "Processo finalizado com sucesso."

        addLog("ProcessId: ${result.processId}")
        Log.d(TAG, "PROCESS ID: ${result.processId}")
    }

    override fun onCameraReady(document: UnicoCheckCameraOpener.Document?) {
        addLog("DocumentCamera pronto.")
        document?.open(documentType, this)
    }

    override fun onCameraReady(cameraOpener: UnicoCheckCameraOpener.Camera) {
        val silentAuthUserId = activeSilentAuthUserId
        if (silentAuthUserId != null) {
            addLog("Coleta em background iniciada.")
            activeSilentAuthUserId = null
            mainHandler.postDelayed({
                createProcess(silentAuthUserId)
            }, 4500)
            return
        }

        addLog("Camera pronta.")
        cameraOpener.open(this)
    }

    override fun onCameraFailed(error: String?) {
        addLog("Falha na câmera: $error")
        textField.text = error
        activeSilentAuthUserId = null
    }

    override fun onSuccessDocument(result: ResultCamera?) {
        addLog("Documento capturado com sucesso.")
        textField.text = "Documento capturado com sucesso."
        result?.encrypted?.let {
            addLog("JWT do documento capturado com sucesso.")
            Log.d(TAG, "JWT COMPLETO DO DOCUMENTO: $it")
        }
    }

    override fun onErrorDocument(error: String?) {
        addLog("Erro no documento: $error")
        textField.text = error
    }
}