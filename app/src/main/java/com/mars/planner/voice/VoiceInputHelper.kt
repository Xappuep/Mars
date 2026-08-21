package com.mars.planner.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

sealed class VoiceResult {
    data class Success(val text: String) : VoiceResult()
    data class Error(val message: String) : VoiceResult()
    data object Unavailable : VoiceResult()
}

class VoiceInputHelper(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(onResult: (VoiceResult) -> Unit) {
        if (!isRecognitionAvailable()) {
            onResult(
                VoiceResult.Error(
                    "Распознавание речи недоступно. Установите русский офлайн-пакет в настройках Google/системы или проверьте микрофон."
                )
            )
            return
        }
        stop()
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Нужен интернет или установленный русский офлайн-пакет распознавания речи. Уже введённый текст не потерян."
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "Не удалось разобрать речь. Попробуйте ещё раз или введите название вручную."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Нужно разрешение на микрофон."
                    else -> "Ошибка распознавания ($error). Текст в поле не изменён."
                }
                onResult(VoiceResult.Error(message))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) {
                    onResult(VoiceResult.Error("Пустой результат. Попробуйте ещё раз."))
                } else {
                    onResult(VoiceResult.Success(text))
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer.startListening(intent)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }
}
