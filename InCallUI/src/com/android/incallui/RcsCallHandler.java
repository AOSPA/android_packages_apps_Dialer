/**
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.incallui;

import java.util.concurrent.ConcurrentHashMap;

/**
 * RcsCallHandler class is a singleton class
 * and this class will handle the RcsCallInfo object of each call.
 * One can get the RcsCallInfo by calling function getRcsCallInfo.
 */
public class RcsCallHandler implements CallList.Listener {

    private static RcsCallHandler sRcsCallHandler;

    /* This mRcsCallInfoContainer is used to save the call vs RcsCallInfo in a hash map
     * the hasmap will be updated accordingly when a call is initiated and when a call is
     * disconnected
     */
    private ConcurrentHashMap<Call, RcsCallInfo> mRcsCallInfoContainer
            = new ConcurrentHashMap<Call, RcsCallInfo>();

    /**
     * makeing it as a singleton class.
     */
    private RcsCallHandler() {
        CallList.getInstance().addListener(this);
    }

    /**
     * getInstance will return a singleton instance of RcsCallHandler
     *
     * @return RcsCallHandler
     */
    public static RcsCallHandler getInstance() {
        if (sRcsCallHandler == null) {
            sRcsCallHandler = new RcsCallHandler();
        }
        return sRcsCallHandler;
    }

    /**
     * getRcsCallInfo function will return the RcsCallInfo from mRcsCallInfoContainer
     * if available.
     *
     * @param Call
     * @return RcsCallInfo, null if did not find RcsCallInfo.
     */
    public RcsCallInfo getRcsCallInfo(Call call) {
        if (call == null || !mRcsCallInfoContainer.containsKey(call)) {
            log("getRcsCallInfo : returning null");
            return null;
        }
        return mRcsCallInfoContainer.get(call);
    }

    /**
     * addRcsCallInfo function will add a hashmap entry of Call and RcsCallInfo.
     *
     * @param Call
     * @param RcsCallInfo
     */
    private void addRcsCallInfo(Call call, RcsCallInfo callInfo) {
        log("addRcsCallInfo : " + call + " RcsCallInfo : " + callInfo);
        if(call != null && callInfo != null) {
            mRcsCallInfoContainer.put(call, callInfo);
        }
        log("mRcsCallInfoContainer content : " + mRcsCallInfoContainer);
    }

    /**
     * removeRcsCallInfo function will remove the hashmap entry of Call.
     *
     * @param Call
     */
    private void removeRcsCallInfo(Call call) {
        log("removeRcsCallInfo: " + call);
        mRcsCallInfoContainer.remove(call);
        log("mRcsCallInfoContainer content : " + mRcsCallInfoContainer);
    }

    private boolean requestAddRcsCallInfo(Call call) {
        if (call != null && getRcsCallInfo(call) == null) {
            addRcsCallInfo(call, new RcsCallInfo(call));
            return true;
        }
        return false;
    }

    @Override
    public void onIncomingCall(Call call) {
        log("onIncomingCall: " + call);
        if (requestAddRcsCallInfo(call)) {
             log("Added IncomingCall");
             return;
         }
    }

    @Override
    public void onUpgradeToVideo(Call call) {
    }

    @Override
    public void onCallListChange(CallList callList) {
        log("onCallListChange: " + callList);
        /*
         * get the call for each state and check if the call can be added to RCS call list.
         * If it is successful then just return from the function.
         */
        Call call = callList.getIncomingCall();
        if (requestAddRcsCallInfo(call)) {
            log("Added IncomingCall");
            return;
        }

        call = callList.getPendingOutgoingCall();
        if (requestAddRcsCallInfo(call)) {
            log("Added PendingOutgoing call");
            return;
        }

        call = callList.getOutgoingCall();
        if (requestAddRcsCallInfo(call)) {
            log("Added outgoing call");
            return;
        }

        call = callList.getActiveCall();
        if (requestAddRcsCallInfo(call)) {
            log("Added active call");
            return;
        }

        call = callList.getBackgroundCall();
        if (requestAddRcsCallInfo(call)) {
            log("Added Background call");
            return;
        }
    }

    @Override
    public void onDisconnect(Call call) {
        log("onDisconnect: " + call);
        removeRcsCallInfo(call);
    }

    private void log(String msg) {
        Log.d(this,msg);
    }
}
