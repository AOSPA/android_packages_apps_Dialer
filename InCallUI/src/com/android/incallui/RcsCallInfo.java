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

import android.content.Context;
import org.codeaurora.rcscommon.CallComposerData;
import org.codeaurora.rcscommon.RcsManager;
import android.os.Bundle;
/**
 * RcsCallInfo will contain the RCS call data with location and shared image data.
 * Each object of this class will be associated with the a perticular call
 * which is sent in constructor.
 */
public class RcsCallInfo {

    public enum AccessPermissionStatus {
        UNKNOWN, GRANTED, NOT_GRANTED
    }

    private byte[] mLocationImageArray = null;

    private boolean isLocationImageReqested = false;

    private Call mCall;

    private AccessPermissionStatus mPermissionStatus = AccessPermissionStatus.UNKNOWN;

    /**
     * Default constructor can not be called as Call object is mandatory to initialize or create
     * RcsCallInfo object.
     */
    private RcsCallInfo() {
    }

    /**
     * Constructor  of RcsCallInfo
     * @param : Call object to handle Enrich call data operations.
     */
    public RcsCallInfo(Call call) {
        mCall = call;
    }

    /**
     * Request to get the location image
     *
     * @param : int width of the image which need to be fetched
     * @param : int height of the image which need to be fetched
     * @param : Context
     */
    public void requestRcsLocationImage(int width, int height, Context context) {
        Log.d(this,"requestRcsLocationImage");
        if (!isLocationImageReqested) {
            isLocationImageReqested = true;
            CallComposerData data = getCallComposerData();
            new InCallRcsImageDownloader(context)
                .requestFetchLocationImage(width, height, data.getLatitude(),
                    data.getLongitude(), new InCallRcsImageDownloader.
                    FetchImageListener() {

                public void onFetchedImage(byte[] arr) {
                    mLocationImageArray = arr;
                    if (RcsCallFragment.getPresenterInstance() != null) {
                        RcsCallFragment.getPresenterInstance().forceUpdateUi();
                    }
                }
            });
        } else {
            Log.d(this, "isLocationImageReqested : " + isLocationImageReqested);
        }
    }

    /**
     * check if any operations are pending to fetch location image.
     * @return boolean true if pending else false.
     */
    public boolean isLocImageReqested() {
        return isLocationImageReqested;
    }

    /**
     * check if the call is enrich call or not
     * @return boolean true if Enrich call else false
     */
    public boolean isEnrichedCall() {
        return getCallComposerData() != null ? getCallComposerData().isValid() : false;
    }

    /**
     * get the callcomposerdata.
     * @return CallComposerData, null if the call is not a RCS call. else the actual
     * RCS call content(CallComposerData)
     */
    public CallComposerData getCallComposerData() {
        if (mCall == null || mCall.getTelecomCall() == null) {
            return null;
        }
        Bundle extras = mCall.getTelecomCall().getDetails().getExtras();
        CallComposerData data = null;
        if (extras == null ||
                extras.getBundle(RcsManager.ENRICH_CALL_INTENT_EXTRA) == null) {
            extras = mCall.getTelecomCall().getDetails().getIntentExtras();
            if (extras != null) {
                data = new CallComposerData(extras.getBundle(
                        RcsManager.ENRICH_CALL_INTENT_EXTRA));
                Log.d(this, "getCallComposerData() from Intent Extras: " + data);
            }
        } else {
            data = new CallComposerData(
                    extras.getBundle(RcsManager.ENRICH_CALL_INTENT_EXTRA));
            Log.d(this, "getCallComposerData() from Extras: " + data);
        }
        return data;
    }

    /**
     * get the permission status to access shared image.
     * @return AccessPermissionStatus, GRANTED if the previous request is granted.
     * NOT_GRANTED if the previous request was not granted.
     * UNKNOWN if the request is not made yet for this call.
     */
    public AccessPermissionStatus getPermissionStatus(){
        return mPermissionStatus;
    }

    /**
     * set the permission status.
     * @param boolean granted. true if GRANTED else false means NOT_GRANTED.
     */
    public void setPermissionStatus(boolean granted) {
        mPermissionStatus = granted ? AccessPermissionStatus.GRANTED
                : AccessPermissionStatus.NOT_GRANTED;
    }

    /**
     * get the location image byte array
     * @return byte array, null if image is null.
     */
    public byte[] getLocationImageArray() {
        return mLocationImageArray;
    }

}
