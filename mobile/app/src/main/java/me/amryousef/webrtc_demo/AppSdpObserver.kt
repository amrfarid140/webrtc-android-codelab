package me.amryousef.webrtc_demo

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class AppSdpObserver : SdpObserver {
    override fun onSetFailure(p0: String?) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateSuccess(p0: SessionDescription?) {
    }

    override fun onCreateFailure(p0: String?) {
    }
}