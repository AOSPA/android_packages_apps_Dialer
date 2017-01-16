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
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telecom.Call.Details;
import android.util.TypedValue;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;

import java.util.Locale;

import org.codeaurora.rcscommon.CallComposerData;
import org.codeaurora.rcscommon.EnrichedCallState;
import org.codeaurora.rcscommon.RcsManager;

/**
 * RcsCallPresenter is a presenter to handle the RcsCallFragement. all rcs relatred
 * view will be handled with RcsCallPresenter.
 */
public class RcsCallPresenter extends Presenter<RcsCallPresenter.RcsCallUi>
        implements InCallStateListener, IncomingCallListener,
        InCallDetailsListener, InCallPresenter.InCallEventListener {

    public interface RcsCallUi extends Ui {
        void updateCallComposerData(RcsCallInfo info);
        Context getContext();
        int getMapImageViewWidth();
        int getMapImageViewHeight();
        void setEnabled(boolean enabled);
        void setVisible(boolean visible);
        void showSmallView(boolean showSmall);
        void showEnrichCallFailed(CallComposerData.PRIORITY priority);
        void showEnrichCallDetail();
        void hideEnrichCallDetail();
        boolean hasReadStoragePermission();
        void requestReadStoragePermission();
        boolean isPermissionPending();
        boolean isInForground();
    }

    public interface ImageBitmapListener {
        void onFetchBitmap(Bitmap bitmap);
    }

    private static final int REQUEST_MAP_IMAGE = 1;
    private static final int REQUEST_SHARED_IMAGE = 2;
    private CallComposerData mCallComposerData = null;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case REQUEST_MAP_IMAGE:
                Call call = (Call) msg.obj;
                requestRcsLocationImage(call);
                break;
            case REQUEST_SHARED_IMAGE:
                call = (Call) msg.obj;
                requestRcsSharedImage(call);
                break;
            }
        }
    };

    private RcsCallHandler mRcsCallHandler = RcsCallHandler.getInstance();

    public RcsCallPresenter() {
        Log.d(this, "constructor");
    }

    @Override
    public void onUiReady(RcsCallUi ui) {
        Log.d(this, "onUiReady");
        super.onUiReady(ui);
        final InCallActivity activity = InCallPresenter.getInstance()
                .getActivity();
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addInCallEventListener(this);
        onStateChange(InCallState.NO_CALLS, InCallPresenter.getInstance()
            .getInCallState(), CallList.getInstance());

    }

    @Override
    public void onUiUnready(RcsCallUi ui) {
        Log.d(this, "onUiUnready");
        super.onUiUnready(ui);
        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallEventListener(this);
        mCallComposerData = null;
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState,
            CallList callList) {
        Log.d(this, "onStateChange");
        updateRichCallView(getCurrentCall());
    }

    private Call getCurrentCall() {
        Call call = null;
        CallList callList = CallList.getInstance();
        InCallState newState = InCallPresenter.getInstance().getInCallState();
        if (newState == InCallState.INCOMING) {
            call = callList.getIncomingCall();
        } else if (newState == InCallState.PENDING_OUTGOING
                || newState == InCallState.OUTGOING) {
            call = callList.getOutgoingCall();
            if (call == null) {
                call = callList.getPendingOutgoingCall();
            }
        } else if (newState == InCallState.INCALL) {
            call = getCallToDisplay(callList);
        }
        return call;
    }

    private void updateRichCallView(Call call) {
        Log.d(this ,"updateRichCallView");
        RcsCallUi ui = getUi();
        if (call != null && ui != null) {

            RcsCallInfo callInfo = getRcsCallInfo(call);
            CallComposerData data = callInfo != null ? callInfo.getCallComposerData() : null;
            //Just for debugging purpose
            Log.d(this ,"call state : " + call.getState());
            Log.d(this ,"data : " + data);
            Log.d(this ,"mCallComposerData : " + mCallComposerData);

            /*
             * Hide the RCS UI for conference call or for video call
             */
            if (call.isConferenceCall() || call.isVideoCall(ui.getContext())) {
                Log.d(this," in conf call or VT call ");
                ui.setVisible(false);
                mCallComposerData = null;
                return;
            }

            /*
             * Update rcs content on UI only if we are updating for the first time
             * and the RCS content is valid or current RCSContent should not be null
             * and should not be same as previous. And new RCS content should be valid.
             */
            if ((mCallComposerData == null && data != null && data.isValid())
                    || (mCallComposerData != null && !mCallComposerData.equals(data)
                        && (data != null && data.isValid()))) {
                /*
                 * If RCS call state is falied then hide the UI
                 * and show a enrich call failed toast message, else show the UI.
                 */
                if (data.getCallState() == EnrichedCallState.FAILED) {
                    Log.d(this,"Enriched call failed");
                    ui.setVisible(false);
                } else {
                    Log.d(this,"Setting visible");
                    ui.setVisible(true);
                }

                /*
                 * For a incoming call or waiting call show a small RCS view.
                 * and disable the RCS view.
                 *
                 * Show enabled and show full view in case of non INCOMING/WAITING call.
                 */
                if(call.getState() == Call.State.INCOMING
                        || call.getState() == Call.State.CALL_WAITING) {
                    ui.setEnabled(false);
                    ui.showSmallView(true);
                } else {
                    ui.setEnabled(true);
                    ui.showSmallView(false);
                }
                updateUi(call);
            } else if ((data != null && !data.isValid()) || data == null) {
                /*
                 * if new data is not valid or if new data is null then hide the UI
                 */
                Log.d(this,"content not valid");
                ui.setVisible(false);
                /*
                 * show the enrich call failed message only once when moving state from
                 * mCallComposerData null to new callcomposerdata call state is FAILED
                 * or from valid mCallComposerData to new callcomposerdata call state is FAILED
                 */
                if ((mCallComposerData == null && data != null
                        && data.getCallState() == EnrichedCallState.FAILED)
                        || (mCallComposerData != null
                        && mCallComposerData.getCallState() != EnrichedCallState.FAILED
                        && data != null && data.getCallState() == EnrichedCallState.FAILED)) {
                    Log.d(this, "show enrich call failed toast message");
                    ui.showEnrichCallFailed(mCallComposerData.getPriority());
                }
                mCallComposerData = data;
            }

            /*
             * For a incoming call or waiting call show a small RCS view.
             * and disable the RCS view.
             */
            if (call.getState() != Call.State.CALL_WAITING
                    && call.getState() != Call.State.INCOMING) {
                ui.showSmallView(false);
                ui.showEnrichCallDetail();
            } else {
                ui.showSmallView(true);
            }

            /*
             * Request to get the Shared Image or Location Image for RCSService.
             */
            if (mCallComposerData != null && mCallComposerData.isValid()) {
                boolean hasMapImgMsg = mHandler.hasMessages(REQUEST_MAP_IMAGE);
                boolean hasShardImgMsg = mHandler.hasMessages(REQUEST_SHARED_IMAGE);

                boolean canReqSharedImg = canRequestSharedImage(call);
                boolean canReqLocImg = canRequestLocImage(call);
                Log.d(this, "has REQUEST_MAP_IMAGE msg : " + hasMapImgMsg
                        + " canReqLocImg : " + canReqLocImg);
                Log.d(this, "has REQUEST_SHARED_IMAGE msg : " + hasShardImgMsg
                         + " canReqSharedImg : " + canReqSharedImg);
                if(!hasMapImgMsg && canReqLocImg) {
                    Message msg = new Message();
                    msg.obj = call;
                    msg.what = REQUEST_MAP_IMAGE;
                    mHandler.sendMessage(msg);
                }
                if(!hasShardImgMsg && canReqSharedImg) {
                    Message msg = new Message();
                    msg = new Message();
                    msg.obj = call;
                    msg.what = REQUEST_SHARED_IMAGE;
                    mHandler.sendMessage(msg);
                }
            }
        }
    }

    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details
            details) {
        Log.d(this, "onDetailsChanged");
        updateRichCallView(getCurrentCall());
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState,
            Call call) {
        Log.d(this, "onIncomingCall");
        updateRichCallView(getCurrentCall());
    }

    /**
     * update the UI according to call.
     * If call is RCS call then RCS content will be updated accordingly on the UI.
     * If the call is non RCS call then no RCS content will be displayed.
     */
    private void updateUi(Call call) {
        Log.d(this ,"updateUi");
        if (call == null) {
            Log.d(this ,"cant update call is null");
            return;
        }
        RcsCallInfo callInfo = getRcsCallInfo(call) ;
        CallComposerData data = callInfo != null ? callInfo.getCallComposerData() : null;
        if(getUi() == null || data == null) {
            Log.d(this ,"UI is null or data is null");
            return;
        }
        getUi().updateCallComposerData(callInfo);
        mCallComposerData = data;
    }

    protected void forceUpdateUi(){
        Log.d(this ,"forceUpdateUi");
        updateUi(getCurrentCall());
    }

    /**
     * request to get the rcs shared image for a call, if available.
     *
     * @param Call
     */
    public void requestRcsSharedImage(Call call) {
        Log.d(this, "requestRcsSharedImage : call: ");
        RcsCallInfo callInfo = getRcsCallInfo(call) ;
        if (call == null || callInfo == null || callInfo.getCallComposerData() == null) {
            Log.d(this, " call or rcscallhelper or enrichcalldata is null");
            return;
        }

        CallComposerData data = callInfo.getCallComposerData();
        if (data != null && data.isValidSharedImageUri() && getUi() != null) {
            Log.d(this, "requesting for permission ");
            getUi().requestReadStoragePermission();
        } else {
            Log.d(this, "not a valid sharedimage or shared image is already downloaded"
                    + "or UI is null, hence ignoring");
        }
    }

    /**
     * request to get the rcs location image for a call, if available.
     *
     * @param Call
     */
    public void requestRcsLocationImage(Call call) {
        Log.d(this, "requestRcsLocationImage : call: " + call);
        int width = getUi() != null ? getUi().getMapImageViewWidth() : 0;
        int height = getUi() != null ? getUi().getMapImageViewHeight() : 0;
        Log.d(this, "requestRcsLocationImage : width : " + width
                    + " height: " + height);

        RcsCallInfo callInfo = getRcsCallInfo(call) ;
        if (call == null || callInfo == null || callInfo.getCallComposerData() == null) {
            Log.d(this, " call or rcscallhelper or enrichcalldata is null");
            return;
        }
        CallComposerData data = callInfo.getCallComposerData();
        if (data != null && data.isValidLocation() && (callInfo.getLocationImageArray() == null
                || callInfo.getLocationImageArray().length == 0)
                && width != 0 && height != 0 && getUi() != null) {

            Log.d(this, "calling requestRcsLocationImage ");

            callInfo.requestRcsLocationImage(width, height,
                    getUi().getContext());
        } else {
            Log.d(this, "not a valid location or location image is already download"
                    + " or view width/height is 0 or UI is null, hence ignoring");
        }
    }

    /**
     * onSharedImageClicked will be called from the RcsCallFragment when shared image view
     * is clicked.
     *
     * @param uri of the image
     */
    public void onSharedImageClicked(Uri uri) {
        Log.d(this,"onSharedImageClicked : " + uri);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        if(uri != null && !uri.toString().startsWith("file")) {
            uri = Uri.parse("file://" + uri.getSchemeSpecificPart());
        }
        intent.setDataAndType(uri, "image/*");
        if (intent.resolveActivity(getUi().getContext().getPackageManager()) != null) {
            getUi().getContext().startActivity(intent);
        } else {
            QtiCallUtils.displayToast(getUi().getContext(),
                    R.string.can_not_launch_image_application);
        }
    }

    /**
     * onMapImageClicked will be called from the RcsCallFragment when Location image view
     * is clicked.
     *
     * @param double latitude
     * @param double longitude
     */
    public void onMapImageClicked(double latitude, double longitude) {
        String uri = String.format(Locale.ENGLISH, "geo:%f,%f?q=%f,%f", latitude,
                longitude, latitude, longitude);
        Log.d(this,"GEO : " + uri);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (intent.resolveActivity(getUi().getContext().getPackageManager()) != null) {
            getUi().getContext().startActivity(intent);
        } else {
            QtiCallUtils.displayToast(getUi().getContext(),
                    R.string.can_not_launch_maps_application);
        }
    }

    /**
     * onReadStoragePermission will be called from the RcsCallFragment when a read storage access
     * permission is either accepted or rejected.
     *
     * @param boolean granted. true if accepted else false.
     */
    public void onReadStoragePermission(boolean granted) {
        Log.d(this,"onReadStoragePermission : " + granted);
        Call call = getCurrentCall();
        if (call != null) {
            RcsCallInfo callInfo = mRcsCallHandler.getRcsCallInfo(call);
            if(callInfo != null) {
                callInfo.setPermissionStatus(granted);
            }
        }
        if (granted) {
            updateUi(call);
        }
    }

    /**
     * Get the highest priority call to display.
     * Goes through the calls and chooses which to return based on priority of which type of call
     * to display to the user.
     *
     * @param CallList
     */
    private Call getCallToDisplay(CallList callList) {
        // Active calls come second.  An active call always gets precedent.
        Call retval = callList.getActiveCall();
        if (retval != null) {
            return retval;
        }
        retval = callList.getDisconnectingCall();
        if (retval != null) {
            return retval;
        }
        retval = callList.getDisconnectedCall();
        if (retval != null) {
            return retval;
        }
        // Then we go to background call.
        retval = callList.getBackgroundCall();
        if (retval != null) {
            return retval;
        }

        // At last, get second background call.
        retval = callList.getSecondBackgroundCall();

        return retval;
    }

    /**
     * This function is used to check if we can request to get shared image.
     *
     * @return  true if there is a valid callcomposerdata attached to call and it is valid
     * and if the callcomposerdata has the valid shared image uri/path
     * and application has the read storage permission
     * and there are no pending request get read storage permission request. Else returns false.
     *
     * Note: this function returns false if application is not having read external storage
     *       permission and the same time call state is INCOMING or CALL_WAITING
     */
    private boolean canRequestSharedImage(Call call) {
        boolean incomingCall = call != null && (call.getState() == Call.State.INCOMING
                || call.getState() == Call.State.CALL_WAITING);

        RcsCallInfo callInfo = mRcsCallHandler.getRcsCallInfo(call);
        CallComposerData data = callInfo.getCallComposerData();

        return data != null && data.isValid()
                && data.isValidSharedImageUri() && call != null
                && (!getUi().hasReadStoragePermission() ? !incomingCall : true)
                && !getUi().isPermissionPending()
                && callInfo != null
                && callInfo.getPermissionStatus() != RcsCallInfo.AccessPermissionStatus.NOT_GRANTED
                && getUi().isInForground();
    }

    /**
     * This function is used to check if we can request to get location image.
     *
     * @return  true if there is a valid callcomposerdata attached to call and it is valid
     * and if the callcomposerdata has the valid location coordinates
     * and there are no pending request to fetch location image
     * and if the location image is not downloaded yet. Else returns false.
     */
    private boolean canRequestLocImage(Call call) {
        RcsCallInfo callInfo = getRcsCallInfo(call);
        CallComposerData data = callInfo.getCallComposerData();
        return data != null && data.isValid()
                && call != null && callInfo != null
                && !callInfo.isLocImageReqested()
                && data.isValidLocation()
                && (callInfo.getLocationImageArray() == null
                || callInfo.getLocationImageArray().length == 0);
    }

    /**
     * getRcsCallInfo function will provide the RcsCallInfo for a
     * perticular call.
     *
     * @return RcsCallInfo, null if RcsCallInfo did not find.
     */
    private RcsCallInfo getRcsCallInfo(Call call) {
        return mRcsCallHandler.getRcsCallInfo(call);
    }

    /**
     * updateImageFromUri function will be called from RcsCallFragment to get the bitmap
     * form a Uri to display the bitmap in an image view.
     *
     * @param Uri, image uri
     * @param ImageBitmapListener callback listener to get the bitmap object
     */
    public void updateImageFromUri(final Uri uri, final ImageBitmapListener listener) {
        Log.d(this, "updateImageFromBytes ");
        AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... params) {
                return BitmapFactory.decodeFile(uri.getSchemeSpecificPart());
            }

            @Override
            protected void onPostExecute(Bitmap image) {
                listener.onFetchBitmap(image);
            }
        };
        task.execute(null, null, null);
    }

    /**
     * updateImageFromBytes function will be called from RcsCallFragment to get the bitmap
     * form a byte array to display the bitmap in an image view.
     *
     * @param byte[], image bytes
     * @param ImageBitmapListener callback listener to get the bitmap object
     */
    public void updateImageFromBytes(final byte[] arr, final ImageBitmapListener listener) {
        Log.d(this, "updateImageFromBytes ");
        AsyncTask<Void, Void, Bitmap> task = new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... params) {
                return BitmapFactory.decodeByteArray(arr, 0, arr.length);
            }

            @Override
            protected void onPostExecute(Bitmap image) {
                listener.onFetchBitmap(image);
            }
        };
        task.execute(null, null, null);
    }

    @Override
    public void onFullscreenModeChanged(boolean isFullscreenMode) {
    }

    @Override
    public void onSecondaryCallerInfoVisibilityChanged(boolean isVisible, int height) {
    }

    @Override
    public void onIncomingVideoAvailabilityChanged(boolean isAvailable) {
    }

    @Override
    public void onSendStaticImageStateChanged(boolean isEnabled) {
    }

    @Override
    public void onAnswerViewGrab(boolean isGrabbed) {
        Log.d(this, "onAnswerViewGrab : " + isGrabbed);
        if(isGrabbed) {
            getUi().hideEnrichCallDetail();
        } else {
            getUi().showEnrichCallDetail();
        }
    }

    @Override
    public void updatePrimaryCallState() {
    }

}
