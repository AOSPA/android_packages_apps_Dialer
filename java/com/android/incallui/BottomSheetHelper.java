/* Copyright (c) 2017, The Linux Foundation. All rights reserved.
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

import android.support.v4.app.FragmentManager;
import android.support.v4.os.UserManagerCompat;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.Call.Details;
import android.telecom.VideoProfile;
import android.view.View;

import com.android.dialer.compat.ActivityCompat;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.IntentUtil;
import com.android.incallui.videotech.ims.ImsVideoTech;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.incallui.videotech.utils.SessionModificationState;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtListenerBaseImpl;
import org.codeaurora.ims.QtiImsExtManager;
import org.codeaurora.ims.utils.QtiImsExtUtils;

public class BottomSheetHelper implements InCallPresenter.InCallEventListener,
  PrimaryCallTracker.PrimaryCallChangeListener {

   private ConcurrentHashMap<String,Boolean> moreOptionsMap;
   private ExtBottomSheetFragment moreOptionsSheet;
   private int voiceNetworkType;
   private boolean mIsHideMe = false;
   private Context mContext;
   private DialerCall mCall;
   private PrimaryCallTracker mPrimaryCallTracker;
   private Resources mResources;
   private static BottomSheetHelper mHelper;
   private boolean mHasSentCancelUpgradeRequest = false;
   private AlertDialog callTransferDialog;
   private AlertDialog modifyCallDialog;
   private AlertDialog mCancelModifyCallDialog;
   private boolean mCanDisablePipMode;
   private static final int BLIND_TRANSFER = 0;
   private static final int ASSURED_TRANSFER = 1;
   private static final int CONSULTATIVE_TRANSFER = 2;
   private static final int INVALID_INDEX = -1;

   /* QtiImsExtListenerBaseImpl instance to handle call deflection response */
   private QtiImsExtListenerBaseImpl imsInterfaceListener =
      new QtiImsExtListenerBaseImpl() {

     /* Handles call deflect response */
     @Override
     public void receiveCallDeflectResponse(int phoneId, int result) {
          LogUtil.w("BottomSheetHelper.receiveCallDeflectResponse:", "result = " + result);
     }

     /* Handles call transfer response */
     @Override
     public void receiveCallTransferResponse(int phoneId, int result) {
          LogUtil.w("BottomSheetHelper.receiveCallTransferResponse", "result: " + result);
     }

     /* Handles cancel call modify response */
     @Override
     public void receiveCancelModifyCallResponse(int phoneId, int result) {
          LogUtil.w("BottomSheetHelper.receiveCancelModifyCallResponse", "result: " + result);
          mHasSentCancelUpgradeRequest = false;
          maybeUpdateCancelModifyCallInMap();
     }
   };

   private BottomSheetHelper() {
     LogUtil.d("BottomSheetHelper"," ");
   }

   public static BottomSheetHelper getInstance() {
     if (mHelper == null) {
       mHelper = new BottomSheetHelper();
     }
     return mHelper;
   }

   public void setUp(Context context) {
     LogUtil.d("BottomSheetHelper","setUp");
     mContext = context;
     mResources = context.getResources();
     final String[][] moreOptions = getMoreOptionsFromRes(R.array.bottom_sheet_more_options);
     moreOptionsMap = prepareSheetOptions(moreOptions);
     mPrimaryCallTracker = new PrimaryCallTracker();
     InCallPresenter.getInstance().addListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().addIncomingCallListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().addInCallEventListener(this);
     mPrimaryCallTracker.addListener(this);
     mCanDisablePipMode = Settings.Global.getInt(mContext.getContentResolver(),
         "disable_pip_mode", 0) != 0;
   }

   public void tearDown() {
     LogUtil.d("BottomSheetHelper","tearDown");
     InCallPresenter.getInstance().removeListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().removeIncomingCallListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().removeInCallEventListener(this);
     mIsHideMe = false;
     if (mPrimaryCallTracker != null) {
       mPrimaryCallTracker.removeListener(this);
       mPrimaryCallTracker = null;
     }
     mContext = null;
     mResources = null;
     moreOptionsMap = null;
     mHasSentCancelUpgradeRequest = false;
   }

   public void updateMap() {
     if (mPrimaryCallTracker == null) {
       LogUtil.w("BottomSheetHelper.updateMap : ", "PrimaryCallTracker is null");
       return;
     }
     mCall = mPrimaryCallTracker.getPrimaryCall();
     LogUtil.i("BottomSheetHelper.updateMap","mCall = " + mCall);

     if (mCall != null && moreOptionsMap != null && mResources != null) {
       maybeUpdateDialpadOptionInMap();
       maybeUpdateDeflectInMap();
       maybeUpdateAddParticipantInMap();
       maybeUpdateTransferInMap();
       maybeUpdateHideMeInMap();
       maybeUpdateManageConferenceInMap();
       maybeUpdateOneWayVideoOptionsInMap();
       maybeUpdateModifyCallInMap();
       maybeUpdatePipModeInMap();
       maybeUpdateCancelModifyCallInMap();
     }
   }

   // Utility function which converts options from string array to HashMap<String,Boolean>
   private static ConcurrentHashMap<String,Boolean> prepareSheetOptions(String[][] answerOptArray) {
     ConcurrentHashMap<String,Boolean> map = new ConcurrentHashMap<String,Boolean>();
     for (int iter = 0; iter < answerOptArray.length; iter ++) {
       map.put(answerOptArray[iter][0],Boolean.valueOf(answerOptArray[iter][1]));
     }
     return map;
   }

   private boolean isOneWayVideoOptionsVisible() {
     final int primaryCallState = mCall.getState();
     final int requestedVideoState = mCall.getVideoTech().getRequestedVideoState();
     return (QtiCallUtils.useExt(mContext) && mCall.hasReceivedVideoUpgradeRequest()
       && VideoProfile.isAudioOnly(mCall.getVideoState())
       && VideoProfile.isBidirectional(requestedVideoState))
       || ((DialerCall.State.INCOMING == primaryCallState
       || DialerCall.State.CALL_WAITING == primaryCallState)
       && QtiCallUtils.isVideoBidirectional(mCall));
   }

   private boolean isModifyCallOptionsVisible() {
     final int primaryCallState = mCall.getState();
     return QtiCallUtils.useExt(mContext) && (DialerCall.State.ACTIVE == primaryCallState
        || DialerCall.State.ONHOLD == primaryCallState)
        && QtiCallUtils.hasVoiceOrVideoCapabilities(mCall)
        && !mCall.hasReceivedVideoUpgradeRequest()
        && !isCancelModifyCallOptionsVisible();
   }

   private boolean isCancelModifyCallOptionsVisible() {
     if (QtiImsExtUtils.isCancelModifyCallSupported(getPhoneId(), mContext)) {
       DialerCall call = mPrimaryCallTracker.getPrimaryCall();
       return !mHasSentCancelUpgradeRequest && (call.getVideoTech().getSessionModificationState()
         == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE);
     }
     return false;
   }

   private void maybeUpdateManageConferenceInMap() {
     /* show manage conference option only for active video conference calls if the call
        has manage conference capability */
     boolean visible = mCall.isVideoCall() && mCall.getState() == DialerCall.State.ACTIVE &&
         mCall.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE);
     moreOptionsMap.put(mResources.getString(R.string.manageConferenceLabel), visible);
   }

   private void maybeUpdatePipModeInMap() {
     /* show Pip mode option only for active video calls if the settings db property
        "disable_pip_mode" is set */
     if (!canDisablePipMode()) {
        return;
     }
     final boolean visible = mCall.isVideoCall() && mCall.getState() == DialerCall.State.ACTIVE
         && !mCall.hasReceivedVideoUpgradeRequest();
     moreOptionsMap.put(mResources.getString(R.string.pipModeLabel), visible);
   }

   public boolean isManageConferenceVisible() {
     if (moreOptionsMap == null || mResources == null) {
         LogUtil.w("isManageConferenceVisible","moreOptionsMap or mResources is null");
         return false;
     }

     return moreOptionsMap.get(mResources.getString(R.string.manageConferenceLabel)).booleanValue()
        && !mCall.hasReceivedVideoUpgradeRequest();
   }

   public void showBottomSheet(FragmentManager manager) {
     LogUtil.d("BottomSheetHelper.showBottomSheet","moreOptionsMap: " + moreOptionsMap);
     moreOptionsSheet = ExtBottomSheetFragment.newInstance(moreOptionsMap);
     moreOptionsSheet.show(manager, null);
   }

   public void dismissBottomSheet() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null || !inCallActivity.isVisible()) {
       LogUtil.w("BottomSheetHelper.dismissBottomSheet",
               "In call activity is either null or not visible");
       return;
     }
     if (moreOptionsSheet != null && moreOptionsSheet.isVisible()) {
       moreOptionsSheet.dismiss();
       moreOptionsSheet = null;
     }
     if (callTransferDialog != null && callTransferDialog.isShowing()) {
       callTransferDialog.dismiss();
       callTransferDialog = null;
     }

     if (modifyCallDialog != null && modifyCallDialog.isShowing()) {
       modifyCallDialog.dismiss();
       modifyCallDialog = null;
     }

     if (mCancelModifyCallDialog != null && mCancelModifyCallDialog.isShowing()) {
       mCancelModifyCallDialog.dismiss();
       mCancelModifyCallDialog = null;
     }
   }

   public void optionSelected(@Nullable String text) {
     //callback for bottomsheet clicks
     LogUtil.d("BottomSheetHelper.optionSelected","text : " + text);
     if (text.equals(mContext.getResources().getString(R.string.add_participant_option_msg))) {
       if (QtiImsExtUtils.isCarrierConfigEnabled(getPhoneId(), mContext,
               "add_multi_participants_enabled")) {
         startAddMultiParticipantActivity();
       } else {
         startAddParticipantActivity();
       }
     } else if (text.equals(mResources.getString(R.string.qti_description_target_deflect))) {
       deflectCall();
     } else if (text.equals(mResources.getString(R.string.qti_description_transfer))) {
       transferCall();
     } else if (text.equals(mResources.getString(R.string.manageConferenceLabel))) {
       manageConferenceCall();
     } else if (text.equals(mResources.getString(R.string.qti_ims_hideMeText_unselected)) ||
         text.equals(mResources.getString(R.string.qti_ims_hideMeText_selected))) {
       hideMeClicked(text.equals(mResources.getString(R.string.qti_ims_hideMeText_unselected)));
     } else if (text.equals(mResources.getString(R.string.dialpad_label))) {
       showDialpad();
     } else if (text.equals(mResources.getString(R.string.video_tx_label))) {
       acceptIncomingCallOrUpgradeRequest(VideoProfile.STATE_TX_ENABLED);
     } else if (text.equals(mResources.getString(R.string.video_rx_label))) {
       acceptIncomingCallOrUpgradeRequest(VideoProfile.STATE_RX_ENABLED);
     } else if (text.equals(mResources.getString(R.string.modify_call_label))) {
       displayModifyCallOptions();
     } else if (text.equals(mResources.getString(R.string.pipModeLabel))) {
       VideoCallPresenter.showPipModeMenu();
     } else if (text.equals(mResources.getString(R.string.cancel_modify_call_label))) {
       displayCancelModifyCallOptions();
     }
     moreOptionsSheet = null;
   }

   public void sheetDismissed() {
     LogUtil.d("BottomSheetHelper.sheetDismissed"," ");
     moreOptionsSheet = null;
   }

   private String[][] getMoreOptionsFromRes(final int resId) {
     TypedArray typedArray = mResources.obtainTypedArray(resId);
     String[][] array = new String[typedArray.length()][];
     for  (int iter = 0;iter < typedArray.length(); iter++) {
       int id = typedArray.getResourceId(iter, 0);
       if (id > 0) {
         array[iter] = mResources.getStringArray(id);
       }
     }
     typedArray.recycle();
     return array;
   }

   public boolean shallShowMoreButton(Activity activity) {
     if (mPrimaryCallTracker != null) {
       DialerCall call = mPrimaryCallTracker.getPrimaryCall();
       if (call != null && activity != null) {
         int primaryCallState = call.getState();
         return !(ActivityCompat.isInMultiWindowMode(activity)
           || call.isEmergencyCall()
           || ((DialerCall.State.isDialing(primaryCallState) ||
           DialerCall.State.CONNECTING == primaryCallState) &&
           !call.isVideoCall())
           || DialerCall.State.DISCONNECTING == primaryCallState
           || call.hasSentVideoUpgradeRequest()
           || !(getPhoneIdExtra(call) != QtiCallConstants.INVALID_PHONE_ID))
           || isCancelModifyCallOptionsVisible();
       }
     }
     LogUtil.w("BottomSheetHelper shallShowMoreButton","returns false");
     return false;
   }

   public void updateMoreButtonVisibility(boolean isVisible, View moreOptionsMenuButton) {
     if (moreOptionsMenuButton == null) {
       return;
     }

     if (isVisible) {
       moreOptionsMenuButton.setVisibility(View.VISIBLE);
     } else {
       dismissBottomSheet();
       moreOptionsMenuButton.setVisibility(View.GONE);
     }
   }

   private int getPhoneIdExtra(DialerCall call) {
     final Bundle extras = call.getExtras();
     return ((extras == null) ? QtiCallConstants.INVALID_PHONE_ID :
         extras.getInt(QtiImsExtUtils.QTI_IMS_PHONE_ID_EXTRA_KEY,
         QtiCallConstants.INVALID_PHONE_ID));
   }

  private boolean isAddParticipantSupported() {
    boolean showAddParticipant = mCall != null
      && mCall.can(DialerCall.CAPABILITY_ADD_PARTICIPANT)
      && UserManagerCompat.isUserUnlocked(mContext)
      && !mCall.hasReceivedVideoUpgradeRequest();
    if (QtiImsExtUtils.isCarrierConfigEnabled(getPhoneId(), mContext,
        "add_participant_only_in_conference")) {
      showAddParticipant = showAddParticipant && (mCall != null) && (mCall.isConferenceCall());
    }
    return showAddParticipant;
  }

  private void maybeUpdateAddParticipantInMap() {
    moreOptionsMap.put(mResources.getString(R.string.add_participant_option_msg),
        isAddParticipantSupported());
  }

  private void startAddParticipantActivity() {
    try {
      mContext.startActivity(QtiCallUtils.getAddParticipantsIntent());
    } catch (ActivityNotFoundException e) {
      LogUtil.e("BottomSheetHelper.startAddParticipantActivity",
          "Activity not found. Exception = " + e);
    }
  }

  private void startAddMultiParticipantActivity() {
    Intent intent = QtiCallUtils.getAddParticipantsIntent(null);
    List<String> childCallIdList = (mCall != null) ? mCall.getChildCallIds() : null;
    if (childCallIdList != null) {
        StringBuffer sb = new StringBuffer();
        for (String tmp: childCallIdList) {
            String number = CallList.getInstance()
                    .getCallById(tmp).getNumber();
            if (number.contains(";")) {
                String[] temp = number.split(";");
                number = temp[0];
            }
            sb.append(number).append(";");
        }
        intent.putExtra("current_participant_list", sb.toString());
    } else {
      LogUtil.e("BottomSheetHelper.startAddMultiParticipantActivity",
          "sendAddMultiParticipantsIntent, childCallIdList null.");
    }
    try {
      mContext.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      LogUtil.e("BottomSheetHelper.startAddMultiParticipantActivity",
          "Activity not found. Exception = " + e);
    }
  }

   /**
    * This API should be called only when there is a call.
    * Caller should handle if INVALID_PHONE_ID is returned.
    */
   public int getPhoneId() {
     if (mPrimaryCallTracker == null) {
       LogUtil.w("BottomSheetHelper.getPhoneId", "mPrimaryCallTracker is null.");
       return QtiCallConstants.INVALID_PHONE_ID;
     }

     final DialerCall call = mPrimaryCallTracker.getPrimaryCall();
     if (call == null) {
       LogUtil.w("BottomSheetHelper.getPhoneId", "primaryCall is null.");
       return QtiCallConstants.INVALID_PHONE_ID;
     }

     final int phoneId = getPhoneIdExtra(call);
     LogUtil.d("BottomSheetHelper.getPhoneId", "phoneId : " + phoneId);
     return phoneId;
   }

   private void maybeUpdateDeflectInMap() {
     final boolean showDeflectCall =
         QtiImsExtUtils.isCallDeflectionSupported(getPhoneId(), mContext) &&
         (mCall.getState() == DialerCall.State.INCOMING ||
         mCall.getState() == DialerCall.State.CALL_WAITING) &&
         !mCall.isVideoCall() && !mCall.hasReceivedVideoUpgradeRequest();
     moreOptionsMap.put(mResources.getString(R.string.qti_description_target_deflect),
         showDeflectCall);
   }

   /**
    * Deflect the incoming call.
    */
   private void deflectCall() {
     LogUtil.enterBlock("BottomSheetHelper.onDeflect");
     if(mCall == null ) {
       LogUtil.w("BottomSheetHelper.onDeflect", "mCall is null");
       return;
     }
     String deflectCallNumber = QtiImsExtUtils.getCallDeflectNumber(
          mContext.getContentResolver());
     /* If not set properly, inform via Log */
     if (deflectCallNumber == null) {
       LogUtil.w("BottomSheetHelper.onDeflect",
            "Number not set. Provide the number via IMS settings and retry.");
       return;
     }
     int phoneId = getPhoneId();
     LogUtil.d("BottomSheetHelper.onDeflect", "mCall:" + mCall +
          "deflectCallNumber:" + deflectCallNumber);
     try {
       LogUtil.d("BottomSheetHelper.onDeflect",
            "Sending deflect request with Phone id " + phoneId +
            " to " + deflectCallNumber);
       new QtiImsExtManager(mContext).sendCallDeflectRequest(phoneId,
            deflectCallNumber, imsInterfaceListener);
     } catch (QtiImsException e) {
       LogUtil.e("BottomSheetHelper.onDeflect", "sendCallDeflectRequest exception " + e);
     }
   }

   private int getCallTransferCapabilities() {
     Bundle extras = mCall.getExtras();
     return (extras == null)? 0 :
          extras.getInt(QtiImsExtUtils.QTI_IMS_TRANSFER_EXTRA_KEY, 0);
   }

   private void maybeUpdateTransferInMap() {
     moreOptionsMap.put(mResources.getString(R.string.qti_description_transfer),
         getCallTransferCapabilities() != 0 && !mCall.hasReceivedVideoUpgradeRequest());
   }

   private void maybeUpdateHideMeInMap() {
     if (!QtiImsExtUtils.shallShowStaticImageUi(getPhoneId(), mContext) ||
         !VideoUtils.hasCameraPermissionAndShownPrivacyToast(mContext)) {
       return;
     }

     LogUtil.v("BottomSheetHelper.maybeUpdateHideMeInMap", " mIsHideMe = " + mIsHideMe);
     String hideMeText = mIsHideMe ? mResources.getString(R.string.qti_ims_hideMeText_selected) :
         mResources.getString(R.string.qti_ims_hideMeText_unselected);
     moreOptionsMap.put(hideMeText, mCall.isVideoCall()
         && mCall.getState() == DialerCall.State.ACTIVE
         && !mCall.hasReceivedVideoUpgradeRequest());
   }

   /**
    * Handles click on hide me button
    * @param isHideMe True if user selected hide me option else false
    */
   private void hideMeClicked(boolean isHideMe) {
     LogUtil.d("BottomSheetHelper.hideMeClicked", " isHideMe = " + isHideMe);
     mIsHideMe = isHideMe;
     if (isHideMe) {
       // Replace "Hide Me" string with "Show Me"
       moreOptionsMap.remove(mResources.getString(R.string.qti_ims_hideMeText_unselected));
       moreOptionsMap.put(mResources.getString(R.string.qti_ims_hideMeText_selected), isHideMe);
     } else {
       // Replace "Show Me" string with "Hide Me"
       moreOptionsMap.remove(mResources.getString(R.string.qti_ims_hideMeText_selected));
       moreOptionsMap.put(mResources.getString(R.string.qti_ims_hideMeText_unselected), !isHideMe);
     }

     /* Click on hideme shall change the static image state i.e. decision
        is made in VideoCallPresenter whether to replace preview video with
        static image or whether to resume preview video streaming */
     InCallPresenter.getInstance().notifyStaticImageStateChanged(isHideMe);
   }

   // Returns TRUE if UE is in hide me mode else returns FALSE
   public boolean isHideMeSelected() {
     LogUtil.v("BottomSheetHelper.isHideMeSelected", "mIsHideMe: " + mIsHideMe);
     return mIsHideMe;
   }

   private void manageConferenceCall() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null) {
       LogUtil.w("BottomSheetHelper.manageConferenceCall", "inCallActivity is null");
       return;
     }

     inCallActivity.showConferenceFragment(true);
   }

   private void showDialpad() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null) {
       LogUtil.w("BottomSheetHelper.showDialpad", "inCallActivity is null");
       return;
     }

     inCallActivity.showDialpadFragment(true, true);
   }

   private void maybeUpdateDialpadOptionInMap() {
     // Enable dialpad option in bottomsheet only for video calls.
     // When video call is held, UI displays onscreen dialpad button
     // similar to volte calls.
     final int primaryCallState = mCall.getActualState();
     final boolean enable = mCall.isVideoCall()
         && primaryCallState != DialerCall.State.INCOMING
         && primaryCallState != DialerCall.State.CALL_WAITING
         && primaryCallState != DialerCall.State.ONHOLD;
     moreOptionsMap.put(mResources.getString(R.string.dialpad_label), enable);
   }

   private void transferCall() {
     LogUtil.enterBlock("BottomSheetHelper.transferCall");
     if(mCall == null ) {
       LogUtil.w("BottomSheetHelper.transferCall", "mCall is null");
       return;
     }
     displayCallTransferOptions();
   }

   /**
    * The function is called when Call Transfer button gets pressed. The function creates and
    * displays call transfer options.
    */
   private void displayCallTransferOptions() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null) {
       LogUtil.e("BottomSheetHelper.displayCallTransferOptions", "inCallActivity is NULL");
       return;
     }
     final ArrayList<CharSequence> items = getCallTransferOptions();
     AlertDialog.Builder builder = new AlertDialog.Builder(inCallActivity)
          .setTitle(R.string.qti_description_transfer);

     DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int item) {
              LogUtil.d("BottomSheetHelper.onCallTransferItemClicked", "" + items.get(item));
              onCallTransferItemClicked(item);
              dialog.dismiss();
         }
     };
     builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), INVALID_INDEX, listener);
     callTransferDialog = builder.create();
     callTransferDialog.show();
   }

   private ArrayList<CharSequence> getCallTransferOptions() {
     final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
     final int transferCapabilities = getCallTransferCapabilities();
     if ((transferCapabilities & QtiImsExtUtils.QTI_IMS_CONSULTATIVE_TRANSFER) != 0) {
       items.add(mResources.getText(R.string.qti_ims_onscreenBlindTransfer));
       items.add(mResources.getText(R.string.qti_ims_onscreenAssuredTransfer));
       items.add(mResources.getText(R.string.qti_ims_onscreenConsultativeTransfer));
     } else if ((transferCapabilities & QtiImsExtUtils.QTI_IMS_BLIND_TRANSFER) != 0) {
       items.add(mResources.getText(R.string.qti_ims_onscreenBlindTransfer));
       items.add(mResources.getText(R.string.qti_ims_onscreenAssuredTransfer));
     }
     return items;
   }

   private void onCallTransferItemClicked(int item) {
     switch(item) {
       case BLIND_TRANSFER:
         callTransferClicked(QtiImsExtUtils.QTI_IMS_BLIND_TRANSFER);
         break;
       case ASSURED_TRANSFER:
         callTransferClicked(QtiImsExtUtils.QTI_IMS_ASSURED_TRANSFER);
         break;
       case CONSULTATIVE_TRANSFER:
         callTransferClicked(QtiImsExtUtils.QTI_IMS_CONSULTATIVE_TRANSFER);
         break;
       default:
         break;
     }
   }

   private void callTransferClicked(int type) {
     String number = QtiImsExtUtils.getCallDeflectNumber(mContext.getContentResolver());
     if (number == null) {
       LogUtil.w("BottomSheetHelper.callTransferClicked", "transfer number error, number is null");
       return;
     }
     int phoneId = getPhoneId();
     try {
       LogUtil.d("BottomSheetHelper.sendCallTransferRequest", "Phoneid-" + phoneId + " type-"
            + type + " number- " + number);
       new QtiImsExtManager(mContext).sendCallTransferRequest(phoneId, type, number,
            imsInterfaceListener);
     } catch (QtiImsException e) {
       LogUtil.e("BottomSheetHelper.sendCallTransferRequest", "exception " + e);
     }
   }

   private void maybeUpdateOneWayVideoOptionsInMap() {
     final boolean showOneWayVideo = isOneWayVideoOptionsVisible();
     moreOptionsMap.put(mResources.getString(R.string.video_rx_label), showOneWayVideo);
     moreOptionsMap.put(mResources.getString(R.string.video_tx_label), showOneWayVideo);
   }

   private void maybeUpdateModifyCallInMap() {
     moreOptionsMap.put(mContext.getResources().getString(R.string.modify_call_label),
        isModifyCallOptionsVisible());
   }

   private void maybeUpdateCancelModifyCallInMap() {
     moreOptionsMap.put(mContext.getResources().getString(R.string.cancel_modify_call_label),
        isCancelModifyCallOptionsVisible());
   }

   private void acceptIncomingCallOrUpgradeRequest(int videoState) {
     if (mCall == null) {
       LogUtil.e("BottomSheetHelper.acceptIncomingCallOrUpgradeRequest", "Call is null. Return");
       return;
     }

     if (mCall.hasReceivedVideoUpgradeRequest()) {
       mCall.getVideoTech().acceptVideoRequest(videoState);
     } else {
       mCall.answer(videoState);
     }
   }

    /**
     * The function is called when Modify Call button gets pressed. The function creates and
     * displays modify call options.
     */
    public void displayModifyCallOptions() {
      final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
      if (inCallActivity == null) {
        LogUtil.e("BottomSheetHelper.displayModifyCallOptions", "inCallActivity is NULL");
        return;
      }

      if (mCall == null) {
        LogUtil.e("BottomSheetHelper.displayModifyCallOptions",
            "Can't display modify call options. Call is null");
        return;
      }

      if (isTtyEnabled(inCallActivity)) {
        LogUtil.w("BottomSheetHelper.displayModifyCallOptions",
            "modify call is allowed only when TTY is off.");
        QtiCallUtils.displayToast(inCallActivity, R.string.video_call_not_allowed_if_tty_enabled);
        return;
      }

      final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
      final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();

      // Prepare the string array and mapping.
      if (QtiCallUtils.hasVoiceCapabilities(mCall) && mCall.isVideoCall()) {
        items.add(mResources.getText(R.string.modify_call_option_voice));
        itemToCallType.add(VideoProfile.STATE_AUDIO_ONLY);
      }

      if (QtiCallUtils.hasReceiveVideoCapabilities(mCall) && !QtiCallUtils.isVideoRxOnly(mCall)) {
        items.add(mResources.getText(R.string.modify_call_option_vt_rx));
        itemToCallType.add(VideoProfile.STATE_RX_ENABLED);
      }

      if (QtiCallUtils.hasTransmitVideoCapabilities(mCall) && !QtiCallUtils.isVideoTxOnly(mCall)) {
        items.add(mResources.getText(R.string.modify_call_option_vt_tx));
        itemToCallType.add(VideoProfile.STATE_TX_ENABLED);
      }

      if (QtiCallUtils.hasReceiveVideoCapabilities(mCall)
          && QtiCallUtils.hasTransmitVideoCapabilities(mCall)
          && !QtiCallUtils.isVideoBidirectional(mCall)) {
        items.add(mResources.getText(R.string.modify_call_option_vt));
        itemToCallType.add(VideoProfile.STATE_BIDIRECTIONAL);
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(inCallActivity);
      builder.setTitle(R.string.modify_call_option_title);

      DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int item) {
            final int selCallType = itemToCallType.get(item);
            Log.v(this, "Videocall: ModifyCall: upgrade/downgrade to "
                + QtiCallUtils.callTypeToString(selCallType));
            changeToVideoClicked(mCall, selCallType);
            dialog.dismiss();
          }
      };
      builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), INVALID_INDEX, listener);
      modifyCallDialog = builder.create();
      modifyCallDialog.show();
    }

    public void displayCancelModifyCallOptions() {
      final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
      if (inCallActivity == null) {
        LogUtil.e("BottomSheetHelper.displayCancelModifyCallOptions", "inCallActivity is NULL");
        return;
      }
      AlertDialog.Builder alertDialog = new AlertDialog.Builder(inCallActivity);
      alertDialog.setTitle(R.string.cancel_modify_call_title);
      alertDialog.setPositiveButton(R.string.cancel_upgrade, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            cancelUpgradeClicked(mCall);
          }
      } );
      alertDialog.setNegativeButton(R.string.not_cancel, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Log.d(this, "not cancel voice call upgrade to video");
          }
      } );
      mCancelModifyCallDialog = alertDialog.create();
      mCancelModifyCallDialog.show();
    }

    /**
     * Sends a session modify request to the telephony framework
     */
    private void changeToVideoClicked(DialerCall call, int videoState) {
      call.getVideoTech().upgradeToVideo(videoState);
    }

    @Override
    public void onSendStaticImageStateChanged(boolean isEnabled) {
      //No-op
    }

    @Override
    public void onSessionModificationStateChange(DialerCall call) {
      //No-op
    }

    /**
      * Cancel the upgrade request.
      */
    private void cancelUpgradeClicked(DialerCall call) {
      LogUtil.enterBlock("BottomSheetHelper.cancelUpgradeClicked");
      if(call == null ) {
        LogUtil.w("BottomSheetHelper.cancelUpgradeClicked", "call is null");
        return;
      }
      try {
        LogUtil.d("BottomSheetHelper.cancelUpgradeClicked",
            "Sending cancel upgrade request with Phone id " + getPhoneId());
        new QtiImsExtManager(mContext).sendCancelModifyCall(getPhoneId(),imsInterfaceListener);
        mHasSentCancelUpgradeRequest = true;
        maybeUpdateCancelModifyCallInMap();
      } catch (QtiImsException e) {
        LogUtil.e("BottomSheetHelper.cancelUpgradeClicked", "sendCancelModifyCall exception " + e);
      }
    }

    /**
     * Handles a change to the fullscreen mode of the app.
     *
     * @param isFullscreenMode {@code true} if the app is now fullscreen, {@code false} otherwise.
     */
    @Override
    public void onFullscreenModeChanged(boolean isFullscreenMode) {
      if (isFullscreenMode) {
        dismissBottomSheet();
      }
    }

    @Override
    public void onPrimaryCallChanged(DialerCall call) {
      dismissBottomSheet();
    }

     /**
     * Returns true if TTY mode is enabled, false otherwise
     */
    private static boolean isTtyEnabled(final Context context) {
      if (context == null) {
        LogUtil.w("BottomSheetHelper.isTtyEnabled", "Context is null...");
        return false;
      }

      final int TTY_MODE_OFF = 0;
      final String PREFERRED_TTY_MODE = "preferred_tty_mode";
      return (android.provider.Settings.Secure.getInt(context.getContentResolver(),
          PREFERRED_TTY_MODE, TTY_MODE_OFF) != TTY_MODE_OFF);
    }

    public boolean canDisablePipMode() {
      return mCanDisablePipMode;
    }

    public PrimaryCallTracker getPrimaryCallTracker() {
      return mPrimaryCallTracker;
    }
}
