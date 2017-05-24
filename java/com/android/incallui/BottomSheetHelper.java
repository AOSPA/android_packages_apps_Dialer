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
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.telecom.Call.Details;
import android.telephony.TelephonyManager;
import com.android.dialer.compat.ActivityCompat;
import com.android.incallui.call.DialerCall;
import com.android.dialer.common.LogUtil;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class BottomSheetHelper {

   private ConcurrentHashMap<String,Boolean> moreOptionsMap;
   private ExtBottomSheetFragment moreOptionsSheet;
   private int voiceNetworkType;
   private Context mContext;
   private DialerCall mCall;
   private PrimaryCallTracker mPrimaryCallTracker;
   private static BottomSheetHelper mHelper;

   private BottomSheetHelper() {
     LogUtil.i("BottomSheetHelper"," ");
   }

   public static BottomSheetHelper getInstance() {
     if (mHelper == null) {
       mHelper = new BottomSheetHelper();
     }
     return mHelper;
   }

   public void setUp(Context context) {
     LogUtil.i("BottomSheetHelper","setUp");
     mContext = context;
     final String[][] moreOptions = getMoreOptionsFromRes(
        mContext.getResources(),R.array.bottom_sheet_more_options);
        moreOptionsMap = ExtBottomSheetFragment.prepareSheetOptions(moreOptions);
     mPrimaryCallTracker = new PrimaryCallTracker();
     InCallPresenter.getInstance().addListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().addIncomingCallListener(mPrimaryCallTracker);
   }

   public void tearDown() {
     LogUtil.i("BottomSheetHelper","tearDown");
     InCallPresenter.getInstance().removeListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().removeIncomingCallListener(mPrimaryCallTracker);
     mPrimaryCallTracker = null;
   }

   public void updateMap() {
     //update as per requirement
     mCall = mPrimaryCallTracker.getPrimaryCall();
     LogUtil.i("BottomSheetHelper.updateMap","mCall = "+mCall);
   }

   public void showBottomSheet(FragmentManager manager) {
     LogUtil.i("BottomSheetHelper.showBottomSheet","moreOptionsMap: "+moreOptionsMap);
     moreOptionsSheet = ExtBottomSheetFragment.newInstance(moreOptionsMap);
     moreOptionsSheet.show(manager, null);
   }

   public void dismissBottomSheet() {
     LogUtil.i("BottomSheetHelper.dismissBottomSheet","moreOptionsSheet: "+moreOptionsSheet);
     if (moreOptionsSheet != null) {
       moreOptionsSheet.dismiss();
     }
   }

   public void optionSelected(@Nullable String text) {
     //callback for bottomsheet clicks
     LogUtil.i("BottomSheetHelper.optionSelected","text : "+text);
     moreOptionsSheet = null;
   }

   public void sheetDismissed() {
     LogUtil.i("BottomSheetHelper.sheetDismissed"," ");
     moreOptionsSheet = null;
   }

   private String[][] getMoreOptionsFromRes(
      final Resources res, final int resId) {
     TypedArray typedArray = res.obtainTypedArray(resId);
     String[][] array = new String[typedArray.length()][];
     for  (int iter = 0;iter < typedArray.length(); iter++) {
       int id = typedArray.getResourceId(iter, 0);
       if (id > 0) {
         array[iter] = res.getStringArray(id);
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
           || DialerCall.State.isDialing(primaryCallState)
           || DialerCall.State.CONNECTING == primaryCallState
           || DialerCall.State.DISCONNECTING == primaryCallState
           || !((getVoiceNetworkType() == TelephonyManager.NETWORK_TYPE_LTE)
           || call.hasProperty(Details.PROPERTY_WIFI)));
       }
     }
     LogUtil.w("BottomSheetHelper shallShowMoreButton","returns false");
     return false;
   }

   private int getVoiceNetworkType() {
     return VERSION.SDK_INT >= VERSION_CODES.N
       ? mContext.getSystemService(TelephonyManager.class).getVoiceNetworkType()
       : TelephonyManager.NETWORK_TYPE_UNKNOWN;
   }
}
