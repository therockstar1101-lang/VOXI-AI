package com.voxi.assistant

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.hardware.camera2.CameraManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import java.util.Locale
import kotlin.math.min

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var heardCommand: TextView
    private var micView: MicOrbView? = null

    private val prefs by lazy {
        getSharedPreferences("voxi_profile", MODE_PRIVATE)
    }

    private var darkMode = true
    private var bg = Color.BLACK
    private var fg = Color.WHITE
    private var muted = Color.LTGRAY
    private val purple = Color.rgb(157, 91, 255)
    private val cyan = Color.rgb(72, 220, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        applyTheme()
        setupSpeech()

        val savedAge = prefs.getInt("age", 0)
        when {
            !prefs.getBoolean("profile_created", false) -> showProfile(false)
            savedAge !in 15..40 -> showBlocked()
            else -> showHome()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun applyTheme() {
        darkMode = prefs.getBoolean("dark_mode", true)
        bg = if (darkMode) Color.rgb(7, 8, 18) else Color.rgb(245, 246, 252)
        fg = if (darkMode) Color.WHITE else Color.rgb(22, 23, 38)
        muted = if (darkMode) Color.rgb(170, 176, 198) else Color.rgb(92, 96, 116)
    }

    private fun rounded(color: Int, radius: Int = 22, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
    }

    private fun makeRoot(): LinearLayout {
        applyTheme()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
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
            setPadding(dp(3), dp(5), dp(3), dp(5))
            if (center) textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        parent.addView(view)
        return view
    }

    private fun addButton(parent: LinearLayout, value: String, action: () -> Unit) {
        val button = TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(117, 66, 225), Color.rgb(35, 164, 220))
            ).apply { cornerRadius = dp(17).toFloat() }
            setPadding(dp(14), dp(15), dp(14), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { setMargins(0, dp(7), 0, dp(7)) }
        parent.addView(button, params)
    }

    private fun addSpace(parent: LinearLayout, height: Int = 12) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addCard(parent: LinearLayout, padding: Int = 16): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            background = rounded(
                if (darkMode) Color.rgb(19, 21, 39) else Color.WHITE,
                22,
                if (darkMode) Color.rgb(47, 49, 77) else Color.rgb(224, 225, 238)
            )
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(7), 0, dp(7)) }
        parent.addView(card, params)
        return card
    }

    private fun makeField(parent: LinearLayout, hintText: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val edit = EditText(this).apply {
            hint = hintText
            inputType = type
            textSize = 16f
            setTextColor(fg)
            setHintTextColor(muted)
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(if (darkMode) Color.rgb(25, 27, 47) else Color.rgb(239, 240, 247), 14)
        }
        parent.addView(edit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { setMargins(0, dp(4), 0, dp(13)) })
        return edit
    }

    // PROFILE
    private fun showProfile(editing: Boolean) {
        val page = makeRoot()
        addSpace(page, 18)
        addText(page, "VOXI", 42f, fg, true, true)
        addText(page, "YOUR VOICE. YOUR ASSISTANT.", 11f, cyan, true, true)
        addSpace(page, 22)

        val card = addCard(page, 20)
        addText(card, if (editing) "EDIT PROFILE" else "WELCOME TO VOXI", 23f, fg, true)
        addText(card, "Create your personal profile", 14f, muted)
        addSpace(card, 12)
        addText(card, "FULL NAME", 12f, cyan, true)
        val name = makeField(card, "Enter your name")
        addText(card, "AGE (15–40)", 12f, cyan, true)
        val age = makeField(card, "Enter your age", InputType.TYPE_CLASS_NUMBER)
        addText(card, "MOBILE NUMBER", 12f, cyan, true)
        val phone = makeField(card, "+91 10-digit mobile number", InputType.TYPE_CLASS_PHONE)

        if (editing) {
            name.setText(prefs.getString("name", ""))
            age.setText(prefs.getInt("age", 0).toString())
            phone.setText(prefs.getString("phone", ""))
        }

        addSpace(page, 8)
        addButton(page, "SAVE & CONTINUE") {
            val enteredName = name.text.toString().trim()
            val enteredAge = age.text.toString().toIntOrNull()
            val enteredPhone = phone.text.toString().filter { it.isDigit() }
            when {
                enteredName.isBlank() -> name.error = "Enter your name"
                enteredAge == null || enteredAge !in 15..40 -> {
                    age.error = "VOXI is only for ages 15 to 40"
                    showBlocked()
                }
                enteredPhone.length != 10 -> phone.error = "Enter a valid 10-digit number"
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
        if (editing) addButton(page, "CANCEL") { showHome() }
        addText(page, "VOXI is for users aged 15 to 40.", 12f, muted, false, true)
    }

    private fun showBlocked() {
        val page = makeRoot()
        addSpace(page, 75)
        addText(page, "VOXI", 44f, fg, true, true)
        addSpace(page, 25)
        addText(page, "ACCESS DENIED", 25f, Color.rgb(255, 83, 110), true, true)
        addText(page, "Sorry! VOXI is only for users aged 15 to 40.", 16f, fg, true, true)
        addSpace(page, 25)
        addButton(page, "TRY AGAIN") { showProfile(false) }
    }

    // FUTURISTIC HOME DASHBOARD
    private fun showHome() {
        val page = makeRoot()
        val name = prefs.getString("name", "User") ?: "User"

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(brand, "VOXI", 30f, fg, true)
        addText(brand, "AI VOICE ASSISTANT", 10f, cyan, true)
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 27f
            setTextColor(fg)
            gravity = Gravity.CENTER
            background = rounded(if (darkMode) Color.rgb(27, 29, 51) else Color.WHITE, 16)
            setPadding(dp(13), dp(8), dp(13), dp(8))
            setOnClickListener { showSettings() }
        }
        top.addView(settings)
        page.addView(top)

        addSpace(page, 20)
        addText(page, "Hello, $name", 23f, fg, true)
        addText(page, "What can I do for you today?", 14f, muted)
        addSpace(page, 14)

        val micCard = addCard(page, 15).apply { gravity = Gravity.CENTER }
        addText(micCard, "VOICE CONTROL", 12f, cyan, true, true)
        addSpace(micCard, 2)
        micView = MicOrbView()
        micCard.addView(micView, LinearLayout.LayoutParams(dp(230), dp(230)).apply {
            gravity = Gravity.CENTER
        })
        addText(micCard, "TAP THE MIC AND SPEAK", 13f, fg, true, true)
        status = addText(micCard, "Ready when you are", 13f, muted, false, true)
        addSpace(micCard, 5)
        addButton(micCard, "🎙  START LISTENING") { startListening() }

        addSpace(page, 12)
        val commandCard = addCard(page, 16)
        addText(commandCard, "WHAT I HEARD", 11f, cyan, true)
        heardCommand = addText(commandCard, "Your spoken command will appear here.", 14f, muted)
        addSpace(page, 6)

        val examples = addCard(page, 16)
        addText(examples, "TRY SAYING", 12f, cyan, true)
        addText(
            examples,
            "“Open YouTube”   •   “Open WhatsApp”\n" +
                "“Open Instagram”   •   “Open Chrome”\n" +
                "“Open Settings”   •   “Open Bluetooth”\n" +
                "“Call 9876543210”   •   “Open Messages”",
            13f, fg
        )

        addSpace(page, 8)
        addButton(page, "👤  MY PROFILE") { showProfile(true) }
    }

    // SETTINGS
    private fun showSettings() {
        val page = makeRoot()
        addText(page, "SETTINGS", 30f, fg, true)
        addText(page, "Personalize your VOXI experience", 14f, muted)
        addSpace(page, 20)

        val appearance = addCard(page)
        addText(appearance, "APPEARANCE", 12f, cyan, true)
        addText(appearance, if (darkMode) "Dark mode is active" else "Light mode is active", 16f, fg, true)
        addButton(page, if (darkMode) "☀  SWITCH TO LIGHT MODE" else "🌙  SWITCH TO DARK MODE") {
            prefs.edit().putBoolean("dark_mode", !darkMode).apply()
            showSettings()
        }

        val profile = addCard(page)
        addText(profile, "YOUR PROFILE", 12f, cyan, true)
        addText(profile, prefs.getString("name", "User") ?: "User", 18f, fg, true)
        addButton(page, "EDIT MY PROFILE") { showProfile(true) }
        addButton(page, "←  BACK TO HOME") { showHome() }
        addSpace(page, 12)
        addText(page, "VOXI  •  VERSION 1.0", 11f, muted, false, true)
    }

    // SPEECH RECOGNITION
    private fun setupSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (::status.isInitialized) status.text = "Listening... speak now"
                micView?.setListening(true)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { micView?.setLevel(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                if (::status.isInitialized) status.text = "Understanding your command..."
                micView?.setListening(false)
            }
            override fun onError(error: Int) {
                micView?.setListening(false)
                if (::status.isInitialized) status.text = speechError(error)
            }
            override fun onResults(results: Bundle?) {
                micView?.setListening(false)
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = words?.firstOrNull().orEmpty()
                if (::status.isInitialized) status.text = if (command.isBlank()) "I didn't catch that. Try again." else "Command received"
                if (::heardCommand.isInitialized) heardCommand.text =
                    if (command.isBlank()) "No speech detected. Tap the mic and try again." else command
                if (command.isNotBlank()) handleCommand(command)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank() && ::heardCommand.isInitialized) heardCommand.text = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun speechError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Try again."
        SpeechRecognizer.ERROR_CLIENT -> "Voice service interrupted. Tap to retry."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet or speech service unavailable."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear that. Please try again."
        else -> "Couldn't recognize speech. Tap to retry."
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        if (recognizer == null) {
            setupSpeech()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            recognizer?.startListening(intent) ?: run {
                if (::status.isInitialized) status.text = "Speech recognition is not available on this phone."
            }
        } catch (e: Exception) {
            if (::status.isInitialized) status.text = "Unable to start microphone. Please retry."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else if (::status.isInitialized) {
            status.text = "Please allow microphone permission to use VOXI."
        }
    }

    // VOICE COMMANDS - existing command actions retained
    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "voxi_reply")
    }

    private fun handleCommand(command: String) {
        val text = command.lowercase(Locale.ROOT)
        when {
            text.contains("youtube") -> openApp("com.google.android.youtube", "YouTube")
            text.contains("whatsapp") -> openApp("com.whatsapp", "WhatsApp")
            text.contains("instagram") -> openApp("com.instagram.android", "Instagram")
            text.contains("chrome") -> openApp("com.android.chrome", "Chrome")
            text.contains("flashlight") || text.contains("torch") -> {
                val turnOn = text.contains("on") || text.contains("enable")
                setFlashlight(turnOn)
            }
            text.contains("volume up") || text.contains("increase volume") || text.contains("volume badhao") -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                speak("Volume increased")
            }
            text.contains("volume down") || text.contains("decrease volume") || text.contains("volume kam") -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                speak("Volume decreased")
            }
            text.contains("silent mode") || text.contains("silent") || text.contains("mute phone") -> {
                setRingerMode(AudioManager.RINGER_MODE_SILENT)
            }
            text.contains("normal mode") || text.contains("ring mode") || text.contains("sound on") -> {
                setRingerMode(AudioManager.RINGER_MODE_NORMAL)
            }
            text.contains("screenshot") || text.contains("screen shot") -> {
                speak("For a screenshot, press your phone's power and volume down buttons together")
            }
            text.contains("screen recording") || text.contains("record screen") -> {
                speak("Swipe down twice from the top of your screen, tap Screen Record, then confirm Start")
            }
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
                } else speak("Please say the phone number")
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

    private fun setRingerMode(mode: Int) {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.ringerMode = mode
            speak(if (mode == AudioManager.RINGER_MODE_SILENT) "Silent mode enabled" else "Normal mode enabled")
        } catch (_: SecurityException) {
            speak("Please allow Do Not Disturb access in settings")
            try { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (_: Exception) { }
        }
    }

    private fun setFlashlight(enabled: Boolean) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 102)
            speak("Please allow camera permission, then repeat the flashlight command")
            return
        }
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) speak("Flashlight is not available on this phone")
            else {
                cameraManager.setTorchMode(cameraId, enabled)
                speak(if (enabled) "Flashlight turned on" else "Flashlight turned off")
            }
        } catch (_: Exception) {
            speak("Unable to control flashlight. Check camera permission")
        }
    }

    private fun openApp(packageName: String, appName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            speak("Opening $appName")
            startActivity(launchIntent)
        } else {
            speak("$appName is not installed")
            if (::status.isInitialized) status.text = "$appName is not installed on this phone."
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
        }
    }

    override fun onDestroy() {
        micView?.stopAnimation()
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    // Animated microphone illustration drawn in native Android Canvas; no extra library needed.
    private inner class MicOrbView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pulse = 0f
        private var listening = false
        private var level = 0f
        private var animator: ValueAnimator? = null

        init {
            startAnimation()
        }

        fun setListening(value: Boolean) {
            listening = value
            invalidate()
        }

        fun setLevel(value: Float) {
            level = value.coerceIn(0f, 10f)
            invalidate()
        }

        private fun startAnimation() {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val base = min(width, height) * 0.39f
            val pulseRadius = base * (1.12f + pulse * 0.13f)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.5).toFloat()
            paint.color = Color.argb(if (listening) 110 else 55, 90, 190, 255)
            canvas.drawCircle(cx, cy, pulseRadius, paint)
            paint.color = Color.argb(if (listening) 90 else 35, 180, 100, 255)
            canvas.drawCircle(cx, cy, base * (1.35f + pulse * 0.08f), paint)

            paint.style = Paint.Style.FILL
            paint.shader = android.graphics.RadialGradient(
                cx, cy, base * 1.15f,
                intArrayOf(Color.rgb(91, 48, 178), Color.rgb(31, 107, 160), Color.rgb(18, 20, 40)),
                null, android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, base, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(3).toFloat()
            paint.color = if (listening) cyan else Color.rgb(183, 139, 255)
            canvas.drawCircle(cx, cy, base * 0.88f, paint)

            // Microphone capsule and stand
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            val micWidth = base * 0.34f
            val micHeight = base * 0.72f
            val micRect = RectF(cx - micWidth / 2, cy - micHeight * 0.62f, cx + micWidth / 2, cy + micHeight * 0.38f)
            canvas.drawRoundRect(micRect, micWidth, micWidth, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(4).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(
                RectF(cx - base * 0.43f, cy - base * 0.28f, cx + base * 0.43f, cy + base * 0.52f),
                0f, 180f, false, paint
            )
            canvas.drawLine(cx, cy + base * 0.52f, cx, cy + base * 0.72f, paint)
            canvas.drawLine(cx - base * 0.22f, cy + base * 0.72f, cx + base * 0.22f, cy + base * 0.72f, paint)

            // Tiny equalizer bars around the mic while listening.
            if (listening) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(3).toFloat()
                for (i in 0..6) {
                    val x = cx - base * 0.72f + i * base * 0.24f
                    val h = base * (0.12f + ((i + level.toInt()) % 4) * 0.055f)
                    canvas.drawLine(x, cy + base * 0.98f, x, cy + base * 0.98f - h, paint)
                }
            }
        }
    }
}
