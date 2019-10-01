package com.justai.aimybox.speechkit.snips

import ai.snips.hermes.IntentMessage
import ai.snips.platform.SnipsPlatformClient
import android.content.Context
import com.justai.aimybox.api.DialogApi
import com.justai.aimybox.logging.Logger
import com.justai.aimybox.model.Request
import com.justai.aimybox.model.Response
import com.justai.aimybox.recorder.AudioRecorder
import com.justai.aimybox.speechtotext.SpeechToText
import com.justai.aimybox.voicetrigger.VoiceTrigger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.map
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext

internal val L = Logger("Snips")

class SnipsKit(
    context: Context,
    assets: SnipsAssets
//    enableDialogue: Boolean = true,
//    enableHotword: Boolean = false,
//    hotwordSensitivity: Float = 0.5f,
//    enableStreaming: Boolean = false,
//    enableInjection: Boolean = false
): CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.IO + Job()

    private val recorder = AudioRecorder("snips")

    private val client = SnipsPlatformClient.Builder(assets.modelDirPath)
        .enableDialogue(true)
        .enableHotword(false)
//        .withHotwordSensitivity(hotwordSensitivity)
        .enableStreaming(true)
//        .enableInjection(enableInjection)
        .build()

    private lateinit var channel: Channel<SpeechToText.Result>
    private lateinit var onHotwordDetected: (phrase: String?) -> Unit

    private var intentResult: IntentMessage? = null
    private var currentSessionId: String? = null

    init {
        client.onPlatformReady = {
            L.d("Platform is ready")
        }

        client.onPlatformError = { error ->
            L.e("Platform error: ${error.message}")
        }

        client.onHotwordDetectedListener = {
            L.d("Wake word detected!")
            onHotwordDetected("")
        }

        client.onIntentDetectedListener = { intent ->
            L.d("Intent detected: ${intent.intent.intentName}")
            intentResult = intent
        }

        client.onIntentNotRecognizedListener = {
            L.d("Intent was not detected")
            intentResult = null
        }

        client.onPartialTextCapturedListener = { msg ->
            L.d("Partial: ${msg.text}")
            channel.offer(SpeechToText.Result.Partial(msg.text))
        }

        client.onTextCapturedListener = { msg ->
            L.d("Recognised: ${msg.text}")
            channel.offer(SpeechToText.Result.Final(msg.text))
            channel.close()
        }

        client.onSessionStartedListener = { msg ->
            L.d("Session was started: ${msg.sessionId}")
            currentSessionId = msg.sessionId
        }

        client.onSessionEndedListener = { msg ->
            L.d("Session was ended: ${msg.sessionId}")
            currentSessionId = null
        }

        client.onListeningStateChangedListener = { state ->
            L.d("Listening: $state")
            when (state) {
                true -> startRecording()
                else -> stopRecording()
            }
        }

        client.connect(context)
    }

    private fun Channel<ByteArray>.convertBytesToShorts() = map { audioBytes ->
        check(audioBytes.size % 2 == 0)
        val audioData = ShortArray(audioBytes.size / 2)
        ByteBuffer.wrap(audioBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(audioData)
        audioData
    }

    private fun startRecording() {
        val audioDataChannel = recorder.startRecordingShorts()
        L.d("Recording was started")

        launch {
            audioDataChannel.consumeEach {
                L.d("Send data ${it.size}")
                client::sendAudioBuffer
            }
        }
    }

    private fun stopRecording() {
        launch {
            recorder.stopAudioRecording()
            L.d("Recording was stopped")
        }
    }

    val speechToText = object : SpeechToText() {

        override suspend fun cancelRecognition() {
            currentSessionId?.let { sessionId ->
                client.endSession(sessionId, null)
            }
        }

        override fun destroy() {
            client.disconnect()
            stopRecording()
        }

        override fun startRecognition(): ReceiveChannel<Result> {
            channel = Channel()
            client.startSession(null, emptyList(), false, null)
            return channel
        }

        override suspend fun stopRecognition() {
            currentSessionId?.let { sessionId ->
                client.endSession(sessionId, null)
            }
        }
    }

    val voiceTrigger = object : VoiceTrigger {
        override fun destroy() {}

        override suspend fun startDetection(
            onTriggered: (phrase: String?) -> Unit,
            onException: (e: Throwable) -> Unit
        ) {
            onHotwordDetected = onTriggered
        }

        override suspend fun stopDetection() {}

    }

    val dialogApi = object : DialogApi {
        override fun destroy() {}

        override suspend fun send(request: Request) = Response(
                query = intentResult?.input,
                action = intentResult?.intent?.intentName)
    }
}
