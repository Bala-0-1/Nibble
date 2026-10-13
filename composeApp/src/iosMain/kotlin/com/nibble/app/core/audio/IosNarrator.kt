package com.nibble.app.core.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.setActive
import platform.AVFAudio.AVSpeechBoundaryImmediate
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSRange
import platform.darwin.NSObject

/**
 * iosMain narrator (§8): AVSpeechSynthesizer + delegate. willSpeakRangeOfSpeechString
 * gives an NSRange; we add the base offset so [spokenChar] is the absolute charOffset.
 * Both platforms report ranges in UTF-16 units, matching the parser's indices.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNarrator : Narrator {

    private val _state = MutableStateFlow(NarratorState.IDLE)
    override val state: StateFlow<NarratorState> = _state

    private val _spokenChar = MutableStateFlow(0)
    override val spokenChar: StateFlow<Int> = _spokenChar

    private val synth = AVSpeechSynthesizer()
    private var fullText: String = ""
    private var baseOffset: Int = 0
    private var rate: Float = 1f

    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            willSpeakRangeOfSpeechString: CValueRange,
            utterance: AVSpeechUtterance,
        ) {
            val loc = willSpeakRangeOfSpeechString.useContents { location.toInt() }
            _spokenChar.value = baseOffset + loc
        }

        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didStartSpeechUtterance: AVSpeechUtterance) {
            _state.value = NarratorState.SPEAKING
        }

        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
            _state.value = NarratorState.IDLE
        }
    }

    init {
        synth.delegate = delegate
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeSpokenAudio, 0uL, null)
            session.setActive(true, null)
        }
    }

    override fun speak(text: String, fromChar: Int, rate: Float) {
        fullText = text
        this.rate = rate
        startFrom(fromChar)
    }

    private fun startFrom(fromChar: Int) {
        baseOffset = fromChar.coerceIn(0, fullText.length)
        _spokenChar.value = baseOffset
        if (synth.speaking) synth.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(fullText.substring(baseOffset))
        utterance.rate = 0.5f * rate
        _state.value = NarratorState.SPEAKING
        synth.speakUtterance(utterance)
    }

    override fun pause() {
        synth.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
        _state.value = NarratorState.PAUSED
    }

    override fun resume() {
        startFrom(_spokenChar.value)
    }

    override fun stop() {
        synth.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
        _state.value = NarratorState.IDLE
    }

    override fun release() {
        synth.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
    }
}

/** Alias for the NSRange CValue delegate parameter. */
@OptIn(ExperimentalForeignApi::class)
private typealias CValueRange = kotlinx.cinterop.CValue<NSRange>
