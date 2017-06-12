/**
 * Copyright (c) 2015-2017 The Linux Foundation. All rights reserved.
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

import android.content.pm.ActivityInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.provider.Settings;

import com.android.dialer.common.LogUtil;

import java.lang.reflect.*;

import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.internal.IExtTelephony;
import org.codeaurora.ims.utils.QtiImsExtUtils;

import com.android.ims.ImsManager;

/**
 * This class contains Qti specific utiltity functions.
 */
public class QtiCallUtils {

    private static String LOG_TAG = "QtiCallUtils";

    /**
     * Returns IExtTelephony handle
     */
    public static IExtTelephony getIExtTelephony() {
        IExtTelephony mExtTelephony = null;
        try {
            Class c = Class.forName("android.os.ServiceManager");
            Method m = c.getMethod("getService",new Class[]{String.class});

            mExtTelephony =
                IExtTelephony.Stub.asInterface((IBinder)m.invoke(null, "extphone"));
        } catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (InvocationTargetException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, " ex: " + e);
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, " ex: " + e);
        }
        return mExtTelephony;
    }

    /**
     * returns true if it is emrgency number else false
     */
    public static boolean isEmergencyNumber(String number) {
        boolean isEmergencyNumber = false;

        try {
            isEmergencyNumber = getIExtTelephony().isEmergencyNumber(number);
        } catch (RemoteException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        } catch (NullPointerException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        }
        return isEmergencyNumber;
    }

    /**
     * returns true if it is local emrgency number else false
     */
    public static boolean isLocalEmergencyNumber(String number) {
        boolean isEmergencyNumber = false;

        try {
            isEmergencyNumber = getIExtTelephony().isLocalEmergencyNumber(number);
        } catch (RemoteException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        } catch (NullPointerException ex) {
            Log.e(LOG_TAG, "Exception : " + ex);
        }
        return isEmergencyNumber;
    }

    /**
    * if true, conference dialer  is enabled.
    */
    public static boolean isConferenceUriDialerEnabled(Context context) {
        boolean isEnhanced4gLteModeSettingEnabled = false;
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            for (int i = 0; i < telephonyManager.getPhoneCount(); i++) {
                isEnhanced4gLteModeSettingEnabled |= ImsManager.getInstance(context, i)
                        .isEnhanced4gLteModeSettingEnabledByUserForSlot();
            }
        return isEnhanced4gLteModeSettingEnabled && ImsManager.isVolteEnabledByPlatform(context);
    }

    /**
    * if true, conference dialer is enabled.
    */
    public static boolean isConferenceDialerEnabled(Context context) {
        boolean isEnhanced4gLteModeSettingEnabled = false;
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            for (int i = 0; i < telephonyManager.getPhoneCount(); i++) {
                if (QtiImsExtUtils.isCarrierConfigEnabled(i, context,
                        "config_enable_conference_dialer")) {
                    isEnhanced4gLteModeSettingEnabled |= ImsManager.getInstance(context, i)
                            .isEnhanced4gLteModeSettingEnabledByUserForSlot();
                }
            }
        return isEnhanced4gLteModeSettingEnabled && ImsManager.isVolteEnabledByPlatform(context);
    }

    /**
    * get intent to start conference dialer
    * with this intent, we can originate an conference call
    */
    public static Intent getConferenceDialerIntent() {
        Intent intent = new Intent("org.codeaurora.confuridialer.ACTION_LAUNCH_CONF_URI_DIALER");
        return intent;
    }

    /**
    * get intent to start conference dialer
    * with this intent, we can originate an conference call
    */
    public static Intent getConferenceDialerIntent(String number) {
        Intent intent = new Intent("org.codeaurora.confdialer.ACTION_LAUNCH_CONF_DIALER");
        intent.putExtra("confernece_number_key", number);
        return intent;
    }

    /**
    * get intent to start conference dialer
    * with this intent, we can add participants to an existing conference call
    */
    public static Intent getAddParticipantsIntent() {
        Intent intent = new Intent("org.codeaurora.confuridialer.ACTION_LAUNCH_CONF_URI_DIALER");
        intent.putExtra("add_participant", true);
        return intent;
    }

     /**
     * used to get intent to start conference dialer
     * with this intent, we can add participants to an existing conference call
     */
    public static Intent getAddParticipantsIntent(String number) {
        Intent intent = new Intent("org.codeaurora.confdialer.ACTION_LAUNCH_CONF_DIALER");
        intent.putExtra("add_participant", true);
        intent.putExtra("current_participant_list", number);
        return intent;
    }

    /**
     * Checks the Settings to conclude on the call deflect support.
     * Returns true if call deflect is possible, false otherwise.
     */
    public static boolean isCallDeflectSupported(Context context) {
        int value = 0;
        try{
            value = android.provider.Settings.Global.getInt(
                    context.getContentResolver(),
                    QtiImsExtUtils.QTI_IMS_DEFLECT_ENABLED);
        } catch(Settings.SettingNotFoundException e) {
            //do Nothing
            LogUtil.e("QtiCallUtils.isCallDeflectSupported", "" + e);
        }
        return (value == 1);
    }

    /** This method converts the QtiCallConstants' Orientation modes to the ActivityInfo
     * screen orientation.
     */
    public static int toScreenOrientation(int orientationMode) {
        switch(orientationMode) {
            case QtiCallConstants.ORIENTATION_MODE_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case QtiCallConstants.ORIENTATION_MODE_PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case QtiCallConstants.ORIENTATION_MODE_DYNAMIC:
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
            default:
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }
}
