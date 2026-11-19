package com.example.workersafety.watch

import android.util.Log
import com.example.workersafety.link.WatchLinkProtocol
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit

class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchLinkProtocol.RECEIPT_PATH) return
        val receipt = WatchLinkProtocol.decode(event.data) ?: return
        try {
            val phone = Tasks.await(Wearable.getCapabilityClient(this).getCapability(
                WatchLinkProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE),
                5, TimeUnit.SECONDS).nodes.singleOrNull() ?: return
            if (phone.id == event.sourceNodeId) PhoneLinkStore.receive(this, receipt)
        } catch (ex: Exception) {
            Log.w("PhoneLink", "Unable to verify receipt source", ex)
        }
    }
}
