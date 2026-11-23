
package com.voxi.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView

    private val prefs by lazy {
        getSharedPreferences("voxi_profile", MODE_PRIVATE)
    }

    private var darkMode = true
    private var bg = Color.BLACK
    private var fg = Color.WHITE
    private var muted = Color.LTGRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        applyTheme()
        setupSpeech()

        val savedAge = prefs.getInt("age", 0)
        if (!prefs.getBoolean("profile_created", false)) {
            showProfile(false)
        } else if (savedAge !in 15..40) {
            // Re-check old or invalid saved profiles before allowing entry.
            showBlocked()
        } else {
            showHome()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun applyTheme() {
        darkMode = prefs.getBoolean("dark_mode", true)

        bg = if (darkMode) Color.rgb(8, 8, 8)
        else Color.rgb(245, 245, 245)

        fg = if (darkMode) Color.WHITE
        else Color.rgb(20, 20, 20)

        muted = if (darkMode) Color.LTGRAY
        else Color.DKGRAY
    }

    private fun makeRoot(): LinearLayout {
        applyTheme()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(22), dp(25), dp(22), dp(25))
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        return root
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int = fg,
        bold: Boolean = false,
        center: Boolean = false
    ): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            if (center) gravity = Gravity.CENTER
            setPadding(dp(4), dp(9), dp(4), dp(9))
        }

        parent.addView(view)
        return view
    }

    private fun addButton(
        parent: LinearLayout,
        value: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = value
            textSize = 15f
            setTextColor(if (darkMode) Color.BLACK else Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(
                if (darkMode) Color.WHITE else Color.rgb(25, 25, 25)
            )
            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        )
        params.setMargins(0, dp(7), 0, dp(7))
        parent.addView(button, params)
    }

    private fun addSpace(parent: LinearLayout, height: Int = 12) {
        val space = View(this)
        parent.addView(space, LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun makeField(
        parent: LinearLayout,
        hintText: String,
        type: Int = InputType.TYPE_CLASS_TEXT
    ): EditText {
        val edit = EditText(this).apply {
            hint = hintText
            inputType = type
            textSize = 16f
            setTextColor(fg)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            backgroundTintList = ColorStateList.valueOf(muted)
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(55)
        )
        params.setMargins(0, dp(4), 0, dp(13))
        parent.addView(edit, params)

        return edit
    }

    // PROFILE SCREEN

    private fun showProfile(editing: Boolean) {
        val page = makeRoot()

        addSpace(page, 25)
        addText(page, "VOXI", 48f, fg, true, true)
        addText(page, "VOICE ASSISTANT", 12f, muted, true, true)

        addSpace(page, 25)

        addText(
            page,
            if (editing) "EDIT PROFILE" else "WELCOME TO VOXI",
            23f, fg, true, true
        )

        addText(page, "Create your personal profile", 14f, muted, false, true)

        addSpace(page, 20)

        addText(page, "FULL NAME", 13f, fg, true)
        val name = makeField(page, "Enter your name")

        addText(page, "YOUR AGE", 13f, fg, true)
        val age = makeField(
            page,
            "Enter your age",
            InputType.TYPE_CLASS_NUMBER
        )

        addText(page, "MOBILE NUMBER", 13f, fg, true)
        val phone = makeField(
            page,
            "+91 10-digit mobile number",
            InputType.TYPE_CLASS_PHONE
        )

        if (editing) {
            name.setText(prefs.getString("name", ""))
            age.setText(prefs.getInt("age", 0).toString())
            phone.setText(prefs.getString("phone", ""))
        }

        addSpace(page, 12)

        addButton(page, "CONTINUE") {
            val enteredName = name.text.toString().trim()
            val enteredAge = age.text.toString().toIntOrNull()
            val enteredPhone = phone.text.toString().filter { it.isDigit() }

            when {
                enteredName.isBlank() -> {
                    name.error = "Enter your name"
                }

                enteredAge == null || enteredAge !in 15..40 -> {
                    // Never save an age outside the permitted 15–40 range.
                    showBlocked()
                }

                enteredPhone.length != 10 -> {
                    phone.error = "Enter a valid 10-digit number"
                }

                else -> {
                    prefs.edit()
                        .putString("name", enteredName)
                        .putInt("age", enteredAge)
                        .putString("phone", enteredPhone)
                        .putBoolean("profile_created", true)
                        .apply()

                    showHome()
                }
            }
        }

        if (editing) {
            addButton(page, "CANCEL") {
                showHome()
            }
        }

        addSpace(page, 20)

        addText(
            page,
            "VOXI is only for users aged 15 to 40.",
            12f, muted, false, true
        )
    }

    // AGE RESTRICTION

    private fun showBlocked() {
        val page = makeRoot()

        addSpace(page, 100)
        addText(page, "VOXI", 48f, fg, true, true)
        addSpace(page, 30)

        addText(page, "ACCESS DENIED", 25f, Color.RED, true, true)

        addText(
            page,
            "Sorry! VOXI is only for users aged 15 to 40.",
            17f, fg, true, true
        )

        addSpace(page, 25)

        addButton(page, "TRY AGAIN") {
            // Return to profile entry without saving the rejected age.
            showProfile(false)
        }
    }

    // HOME DASHBOARD

    private fun showHome() {
        val page = makeRoot()

        val name = prefs.getString("name", "User") ?: "User"

        addText(page, "VOXI", 42f, fg, true, true)
        addText(page, "VOICE ASSISTANT", 12f, muted, true, true)

        addSpace(page, 18)

        addText(page, "Hello, $name 👋", 24f, fg, true)
        addText(page, "Your personal voice assistant", 14f, muted)

        addSpace(page, 35)

        addText(page, "◉", 85f, fg, true, true)
        addText(page, "YOUR VOICE ASSISTANT", 15f, fg, true, true)

        status = addText(
            page,
            "Your assistant is ready",
            15f, muted, false, true
        )

        addSpace(page, 15)

        addButton(page, "🎙  TAP TO SPEAK") {
            startListening()
        }

        addSpace(page, 20)

        addText(page, "TRY SAYING", 13f, muted, true)

        addText(
            page,
            "• Open YouTube\n" +
            "• Open WhatsApp\n" +
            "• Open Instagram\n" +
            "• Open Chrome\n" +
            "• Open Settings\n" +
            "• Open Bluetooth\n" +
            "• Call 9876543210\n" +
            "• Open Messages",
            14f, fg
        )

        addSpace(page, 25)

        addButton(page, "👤  MY PROFILE") {
            showProfile(true)
        }

        addButton(page, "⚙  SETTINGS") {
            showSettings()
        }
    }

    // SETTINGS

    private fun showSettings() {
        val page = makeRoot()

        addText(page, "SETTINGS", 30f, fg, true, true)
        addSpace(page, 25)

        addText(page, "APPEARANCE", 15f, muted, true)

        addButton(
            page,
            if (darkMode) "☀ SWITCH TO LIGHT MODE"
            else "🌙 SWITCH TO DARK MODE"
        ) {
            prefs.edit()
                .putBoolean("dark_mode", !darkMode)
                .apply()
            showSettings()
        }

        addSpace(page, 15)

        addText(page, "PROFILE", 15f, muted, true)

        addButton(page, "EDIT MY PROFILE") {
            showProfile(true)
        }

        addButton(page, "BACK TO HOME") {
            showHome()
        }

        addSpace(page, 25)

        addText(page, "VOXI • VERSION 1.0", 12f, muted, false, true)
    }

    // SPEECH RECOGNITION

    private fun setupSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                if (::status.isInitialized) status.text = "Listening..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (::status.isInitialized) status.text = "Processing..."
            }

            override fun onError(error: Int) {
                if (::status.isInitialized) {
                    status.text = "Try again. Tap microphone."
                }
            }

            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                val command = words?.firstOrNull().orEmpty()

                if (::status.isInitialized) {
                    status.text = "You said: $command"
                }

                if (command.isNotBlank()) handleCommand(command)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }

        try {
            recognizer?.startListening(intent)
                ?: run {
                    status.text = "Speech recognition unavailable"
                }
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
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else if (::status.isInitialized) {
            status.text = "Microphone permission required"
        }
    }

    // VOICE COMMANDS

    private fun speak(message: String) {
        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "voxi_reply"
        )
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
                startActivity(
                    Intent(android.provider.Settings.ACTION_SETTINGS)
                )
            }

            text.contains("mobile data") || text.contains("internet") -> {
                speak("Opening internet settings")
                startActivity(
                    Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                )
            }

            text.contains("bluetooth") -> {
                speak("Opening Bluetooth settings")
                startActivity(
                    Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                )
            }

            text.contains("call") -> {
                val number = text.filter { it.isDigit() }

                if (number.isNotEmpty()) {
                    speak("Opening dialer")
                    startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    )
                } else {
                    speak("Please say the phone number")
                }
            }

            text.contains("message") || text.contains("sms") -> {
                speak("Opening messages")
                startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                    }
                )
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
            if (::status.isInitialized) status.text = "$appName not found"
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
