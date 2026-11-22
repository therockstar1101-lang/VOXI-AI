package com.voxi.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        createScreen()

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "Listening..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    status.text = "Processing..."
                }
                override fun onError(error: Int) {
                    status.text = "Try again. Tap microphone."
                }
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    val command = words?.firstOrNull().orEmpty()
                    status.text = "You said: $command"
                    if (command.isNotBlank()) handleCommand(command)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            status.text = "Speech recognition unavailable"
        }
    }

    private fun createScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 40)
            setBackgroundColor(Color.rgb(8, 8, 8))
        }

        val logo = TextView(this).apply {
            text = "VOXI"
            textSize = 50f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        }

        val subtitle = TextView(this).apply {
            text = "VOICE ASSISTANT"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
        }

        status = TextView(this).apply {
            text = "Your voice assistant is ready"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 55, 0, 35)
        }

        val mic = Button(this).apply {
            text = "🎙  TAP TO SPEAK"
            textSize = 17f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setOnClickListener { startListening() }
        }

        val hint = TextView(this).apply {
            text = "Try: Open YouTube • Open WhatsApp • Open Settings"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 35, 0, 0)
        }

        root.addView(logo)
        root.addView(subtitle)
        root.addView(status)
        root.addView(mic)
        root.addView(hint)
        setContentView(root)
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }

        try {
            recognizer?.startListening(intent)
                ?: run { status.text = "Speech recognition unavailable" }
        } catch (e: Exception) {
            status.text = "Unable to start microphone"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            status.text = "Microphone permission required"
        }
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "voxi_reply")
    }

    private fun handleCommand(command: String) {
        val text = command.lowercase(Locale.ROOT)

        when {
            text.contains("youtube") ->
                openApp("com.google.android.youtube", "YouTube")

            text.contains("whatsapp") ->
                openApp("com.whatsapp", "WhatsApp")

            text.contains("instagram") ->
                openApp("com.instagram.android", "Instagram")

            text.contains("chrome") ->
                openApp("com.android.chrome", "Chrome")

            text.contains("settings") -> {
                speak("Opening settings")
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }

            text.contains("mobile data") || text.contains("internet") -> {
                speak("Opening internet settings")
                startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            }

            text.contains("bluetooth") -> {
                speak("Opening Bluetooth settings")
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }

            text.contains("call") -> {
                val number = text.filter { it.isDigit() }
                if (number.isNotEmpty()) {
                    speak("Opening dialer")
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                } else {
                    speak("Please say the phone number")
                }
            }

            text.contains("message") || text.contains("sms") -> {
                speak("Opening messages")
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                })
            }

            else -> speak("Sorry, I don't know that command yet")
        }
    }

    private fun openApp(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            speak("Opening $appName")
            startActivity(launchIntent)
        } else {
            speak("$appName is not installed")
            status.text = "$appName not found"
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
