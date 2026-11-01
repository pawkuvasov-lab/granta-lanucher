package ru.granta.carlauncher.services

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TrackQueueItem(
    val queueId: Long,
    val title: String,
    val artist: String,
    val durationSec: Int
)

data class MediaState(
    val title: String = "Moby - Go",
    val artist: String = "Moby",
    val isPlaying: Boolean = true,
    val positionSec: Int = 0,
    val durationSec: Int = 205,
    val source: String = "BLUETOOTH", // "BLUETOOTH" или "RADIO"
    val btDeviceName: String = "iPhone",
    val radioFreqMhz: Float = 101.7f,
    val queue: List<TrackQueueItem> = emptyList()
)

data class NavManeuverState(
    val distanceText: String = "Через 200 м",
    val instruction: String = "поворот налево",
    val etaSummary: String = "До финиша: 12 км • 18 мин",
    val speedLimitKmh: Int = 60,
    val turnType: String = "LEFT"
)

/**
 * Сервис интеграции с реальным железом Android-магнитолы:
 * 1. Принимает метаданные музыки с телефона по Bluetooth через стандартный Android MediaSession (AVRCP 1.4/1.6)
 *    И через системные Broadcast-интенты китайских прошивок ГУ (FYT / Teyes / Topway TS10 / Microntek).
 * 2. Управляет физическим FM-чипом магнитолы (приём с антенны через MCU сервис com.syu.radio / com.ts.MainUI).
 * 3. Читает уведомления Яндекс.Навигатора для виджета манёвров.
 */
class CarMediaAndNavListener : NotificationListenerService() {

    private var activeController: MediaController? = null

    // Приёмник метаданных от встроенных Bluetooth-стеков автомагнитол (Teyes / FYT / TS10 / MTC)
    private val mcuBtAndRadioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                "com.android.music.metachanged",
                "com.android.music.playstatechanged",
                "com.ts.bt.music.info",
                "com.syu.bt.avrcp",
                "com.microntek.bt.report" -> {
                    val track = intent.getStringExtra("track")
                        ?: intent.getStringExtra("title")
                        ?: intent.getStringExtra("music_name")
                    val artist = intent.getStringExtra("artist")
                        ?: intent.getStringExtra("singer")
                        ?: "Bluetooth"
                    val playing = intent.getBooleanExtra("playing", true)
                    if (!track.isNullOrBlank()) {
                        _mediaState.value = _mediaState.value.copy(
                            title = "$artist - $track",
                            artist = artist,
                            isPlaying = playing,
                            source = "BLUETOOTH"
                        )
                    }
                }
                // Обратный ответ от аппаратного FM-тюнера магнитолы (текущая частота с антенны)
                "com.syu.radio.freq",
                "com.ts.main.radio.freq",
                "com.microntek.radio.report" -> {
                    val rawFreq = intent.getIntExtra("freq", 10170)
                    val mhz = if (rawFreq > 1000) rawFreq / 100f else rawFreq / 10f
                    _mediaState.value = _mediaState.value.copy(
                        source = "RADIO",
                        radioFreqMhz = mhz,
                        title = "FM ${String.format("%.1f", mhz)} МГц"
                    )
                }
            }
        }
    }

    companion object {
        private val _mediaState = MutableStateFlow(MediaState())
        val mediaState: StateFlow<MediaState> = _mediaState

        private val _navState = MutableStateFlow(NavManeuverState())
        val navState: StateFlow<NavManeuverState> = _navState

        var instance: CarMediaAndNavListener? = null

        fun skipToQueueItem(queueId: Long) {
            instance?.activeController?.transportControls?.skipToQueueItem(queueId)
        }

        fun playPause(context: Context? = null) {
            val ctrl = instance?.activeController
            if (ctrl != null) {
                if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    ctrl.transportControls.pause()
                } else {
                    ctrl.transportControls.play()
                }
            } else {
                // Универсальная отправка аппаратного медиа-ключа (работает со всеми Bluetooth-стеками MCU)
                sendHardwareMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }

        fun next(context: Context? = null) {
            val ctrl = instance?.activeController
            if (ctrl != null) {
                ctrl.transportControls.skipToNext()
            } else {
                sendHardwareMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            }
        }

        fun previous(context: Context? = null) {
            val ctrl = instance?.activeController
            if (ctrl != null) {
                ctrl.transportControls.skipToPrevious()
            } else {
                sendHardwareMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }

        fun seekTo(posMs: Long) = instance?.activeController?.transportControls?.seekTo(posMs)

        private fun sendHardwareMediaKey(context: Context?, keyCode: Int) {
            val ctx = context ?: instance ?: return
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }

        /**
         * Управление физическим FM-чипом магнитолы (антенный вход TEF6686 / TDA7708 / Si4755):
         * Отправляет команду штатному MCU-сервису радио на перестройку частоты и вывод звука с антенны.
         */
        fun switchToFmRadio(context: Context, frequencyMhz: Float? = null) {
            val targetFreq = frequencyMhz ?: _mediaState.value.radioFreqMhz
            _mediaState.value = _mediaState.value.copy(
                source = "RADIO",
                radioFreqMhz = targetFreq,
                title = "FM ${String.format("%.1f", targetFreq)} МГц"
            )

            // 1. Отправка фонового Broadcast для MCU-радио чипов (FYT / Teyes / Topway / Microntek)
            val freqInt100 = (targetFreq * 100).toInt() // например 10170 для 101.7 МГц
            val mcuIntents = listOf(
                Intent("com.syu.ms.action.RADIO_FREQ").putExtra("freq", freqInt100),
                Intent("com.ts.main.radio.SET_FREQ").putExtra("freq", freqInt100),
                Intent("com.microntek.radio.setfreq").putExtra("freq", freqInt100)
            )
            mcuIntents.forEach { context.sendBroadcast(it) }

            // 2. Если требуется открыть или активировать системный радио-процесс
            val radioPackages = listOf(
                "com.ts.MainUI",
                "com.syu.radio",
                "com.android.fmradio",
                "com.microntek.radio"
            )
            for (pkg in radioPackages) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.putExtra("freq", freqInt100)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this

        val filter = IntentFilter().apply {
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.ts.bt.music.info")
            addAction("com.syu.bt.avrcp")
            addAction("com.microntek.bt.report")
            addAction("com.syu.radio.freq")
            addAction("com.ts.main.radio.freq")
            addAction("com.microntek.radio.report")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mcuBtAndRadioReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(mcuBtAndRadioReceiver, filter)
        }

        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, CarMediaAndNavListener::class.java)
        msm.addOnActiveSessionsChangedListener({ controllers ->
            bindController(controllers?.firstOrNull())
        }, component)
        bindController(msm.getActiveSessions(component).firstOrNull())
    }

    private fun bindController(controller: MediaController?) {
        activeController = controller ?: return
        updateFromController(controller)
        controller.registerCallback(object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = updateFromController(controller)
            override fun onPlaybackStateChanged(state: PlaybackState?) = updateFromController(controller)
            override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = updateFromController(controller)
        })
    }

    private fun updateFromController(controller: MediaController) {
        val meta = controller.metadata
        val state = controller.playbackState
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Bluetooth"
        val track = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Аудиопоток"
        val dur = ((meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 205000L) / 1000L).toInt()
        val pos = ((state?.position ?: 0L) / 1000L).toInt()

        val queueItems = controller.queue?.map { item ->
            TrackQueueItem(
                queueId = item.queueId,
                title = item.description.title?.toString() ?: "Трек",
                artist = item.description.subtitle?.toString() ?: "",
                durationSec = 200
            )
        } ?: emptyList()

        _mediaState.value = _mediaState.value.copy(
            title = "$artist - $track",
            artist = artist,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            positionSec = pos,
            durationSec = if (dur > 0) dur else 205,
            source = "BLUETOOTH",
            queue = queueItems
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg.contains("ru.yandex.yandexnavi") || pkg.contains("ru.yandex.yandexmaps")) {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            _navState.value = _navState.value.copy(
                distanceText = title,
                instruction = text.ifEmpty { "по маршруту" },
                etaSummary = subText.ifEmpty { _navState.value.etaSummary }
            )
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(mcuBtAndRadioReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
