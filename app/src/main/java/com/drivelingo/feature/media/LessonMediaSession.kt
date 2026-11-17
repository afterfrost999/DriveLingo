package com.drivelingo.feature.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.drivelingo.app.MainActivity
import com.drivelingo.feature.lesson.LessonController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 수업을 "오디오 레슨" 미디어 세션으로 노출한다 (Pleos Music/Audio 카테고리 필수 항목).
 *
 * - 운전대 재생/일시정지 키와 차량 미디어 화면의 버튼이 [LessonController] 로 간다.
 *   재생 = 재개(없으면 새 수업 시작, 사용자가 누른 것이므로 안전 규칙에 맞음), 일시정지 = 사용자 일시정지,
 *   이전 = 마지막 문장 다시 듣기, 정지 = 중단 지점 저장 후 멈춤.
 * - 세션은 앱 프로세스와 같이 살고, [LessonMediaService] 는 이 세션의 토큰만 내보낸다.
 *   그래서 미디어 화면이 서비스에 붙기 전에도 adb·운전대 키로 시험할 수 있다(docs/testing.md).
 */
class LessonMediaSession(
    context: Context,
    private val lessons: LessonController,
    scope: CoroutineScope,
) {
    private val session = MediaSessionCompat(context, TAG)

    val token: MediaSessionCompat.Token get() = session.sessionToken

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.i(TAG, "play")
            lessons.play()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: android.os.Bundle?) {
            Log.i(TAG, "playFromMediaId $mediaId")
            if (mediaId == LessonMediaService.MEDIA_ID_TODAY) lessons.play()
        }

        override fun onPause() {
            Log.i(TAG, "pause")
            lessons.pause()
        }

        override fun onSkipToPrevious() {
            Log.i(TAG, "repeat")
            lessons.repeat()
        }

        override fun onStop() {
            Log.i(TAG, "stop")
            lessons.stop()
        }
    }

    init {
        session.setCallback(callback)
        session.setSessionActivity(
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        publish(LessonPlaybackMapper.map(null))
        session.isActive = true

        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            lessons.session
                .flatMapLatest { it?.state ?: flowOf(null) }
                .map(LessonPlaybackMapper::map)
                .distinctUntilChanged()
                .collect(::publish)
        }
    }

    private fun publish(playback: LessonPlayback) {
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(playback.state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(playback.actions)
                .build(),
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, LessonMediaService.MEDIA_ID_TODAY)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playback.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, playback.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, ARTIST)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, playback.subtitle)
                .build(),
        )
    }

    private companion object {
        const val TAG = "LessonMedia"
        const val ARTIST = "말차"
    }
}
