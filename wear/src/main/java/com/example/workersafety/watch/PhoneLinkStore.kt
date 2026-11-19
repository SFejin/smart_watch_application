package com.example.workersafety.watch

import android.content.Context
import com.example.workersafety.link.WatchLinkProtocol
import com.example.workersafety.link.WatchLinkProtocol.Result

/** Separate transport status from sensor status. An old callback cannot overwrite a new send. */
object PhoneLinkStore {
    private fun prefs(context: Context) = context.getSharedPreferences(SensorService.PREFS, Context.MODE_PRIVATE)

    @Synchronized fun begin(context: Context, id: String) {
        prefs(context).edit().putString("pending_summary_id", id).remove("receipt_result")
            .putString("delivery", "전송 중 · 휴대폰 수신 대기").apply()
    }

    @Synchronized fun queued(context: Context, id: String, message: String) {
        val p = prefs(context)
        if (p.getString("pending_summary_id", null) != id || p.contains("receipt_result")) return
        p.edit().putString("delivery", message).apply()
    }

    @Synchronized fun receive(context: Context, receipt: WatchLinkProtocol.Receipt) {
        val p = prefs(context)
        if (!WatchLinkProtocol.shouldApply(p.getString("pending_summary_id", null),
                p.getString("receipt_result", null) == Result.ACCEPTED.name, receipt)) return
        val message = when (receipt.result) {
            Result.ACCEPTED -> "휴대폰 수신 완료"
            Result.SIGN_IN_REQUIRED -> "휴대폰 로그인 후 측정을 다시 시작하세요"
            Result.NEW_WINDOW_REQUIRED -> "측정 종료 후 다시 시작하세요"
            Result.INVALID_DATA -> "측정 데이터 또는 기기 시각을 확인하세요"
            Result.WATCH_MISMATCH -> "워치가 변경되었습니다 · 휴대폰에 다시 로그인하세요"
        }
        p.edit().putString("receipt_result", receipt.result.name).putString("delivery", message).apply()
    }
}
