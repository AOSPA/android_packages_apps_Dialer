/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.voicemail.impl;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import java.util.List;

/**
 * Helper for manipulating intents or components with subscription-related information.
 *
 * <p>In settings, subscription ids and labels are passed along to indicate that settings are being
 * changed for particular subscriptions. This helper provides functions for helping extract this
 * info and perform common operations using this info.
 */
public class SubscriptionInfoHelper {
  public static final int NO_SUB_ID = -1;

  public static final int INVALID_SIM_SLOT_INDEX = -1;

  // Extra on intent containing the id of a subscription.
  public static final String SUB_ID_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
  // Extra on intent containing the label of a subscription.
  private static final String SUB_LABEL_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

  private static Context mContext;

  private int mSubId = NO_SUB_ID;
  private int mSlotIndex = INVALID_SIM_SLOT_INDEX;
  private String mSubLabel;
  private PhoneAccountHandle mPhoneAccountHandle;

  /** Instantiates the helper, by parsing the subscription id and label from the phone account. */
  public SubscriptionInfoHelper(Context context, PhoneAccountHandle phoneAccountHandle) {
    mContext = context;
    SubscriptionManager sm = SubscriptionManager.from(mContext);
    List<SubscriptionInfo> subInfoList = sm.getActiveSubscriptionInfoList();
    if (phoneAccountHandle != null && !TextUtils.isEmpty(phoneAccountHandle.getId())
        && subInfoList != null) {
      for (SubscriptionInfo subInfo : subInfoList) {
        if (phoneAccountHandle.getId().startsWith(subInfo.getIccId())) {
          mSubId = subInfo.getSubscriptionId();
          mSubLabel = subInfo.getDisplayName().toString();
          mSlotIndex = subInfo.getSimSlotIndex();
          break;
        }
      }
    }
  }

  public SubscriptionInfoHelper(Context context, String accountId) {
    mContext = context;
    SubscriptionManager sm = SubscriptionManager.from(mContext);
    List<SubscriptionInfo> subInfoList = sm.getActiveSubscriptionInfoList();
    if (!TextUtils.isEmpty(accountId)
        && subInfoList != null) {
      for (SubscriptionInfo subInfo : subInfoList) {
        if (accountId.startsWith(subInfo.getIccId())) {
          mSubId = subInfo.getSubscriptionId();
          mSubLabel = subInfo.getDisplayName().toString();
          mSlotIndex = subInfo.getSimSlotIndex();
          break;
        }
      }
    }
  }

  public Intent getConfiguringVoiceMailIntent() {
    Intent intent = new Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL);
    if (hasSubId()) {
      intent.putExtra(SUB_ID_EXTRA, mSubId);
      if (!TextUtils.isEmpty(mSubLabel)) {
        intent.putExtra(SUB_LABEL_EXTRA, mSubLabel);
      }
    }
    return intent;
  }

  public boolean hasSubId() {
    return mSubId != NO_SUB_ID;
  }

  public int getSubId() {
    return mSubId;
  }

  public int getSimSlotIndex() {
    return mSlotIndex;
  }
}
