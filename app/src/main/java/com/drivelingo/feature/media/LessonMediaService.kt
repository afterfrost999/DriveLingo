package com.drivelingo.feature.media

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import com.drivelingo.app.DriveLingoApplication

/**
 * 차량 미디어 화면(AAOS Media Center)이 붙는 MediaBrowserService.
 *
 * 탐색 트리는 "오늘의 수업" 하나다. 누르면 [LessonMediaSession] 의 onPlayFromMediaId 로 수업이 시작·재개된다.
 * 재생 제어는 모두 앱 수명의 미디어 세션이 처리하고, 이 서비스는 그 토큰을 알려 주기만 한다.
 *
 * 남은 일: 앱 화면 없이 백그라운드에서 오래 재생하려면 mediaPlayback 포그라운드 서비스가 필요하다(docs/policy-review.md).
 */
class LessonMediaService : MediaBrowserServiceCompat() {

    private val container get() = (application as DriveLingoApplication).container

    override fun onCreate() {
        super.onCreate()
        sessionToken = container.mediaSession.token
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }
        val plan = container.lessons.nextPlan()
        val today = MediaDescriptionCompat.Builder()
            .setMediaId(MEDIA_ID_TODAY)
            .setTitle("오늘의 수업")
            .setSubtitle(plan.title)
            .build()
        result.sendResult(mutableListOf(MediaItem(today, MediaItem.FLAG_PLAYABLE)))
    }

    companion object {
        const val ROOT_ID = "root"
        const val MEDIA_ID_TODAY = "lesson_today"
    }
}
