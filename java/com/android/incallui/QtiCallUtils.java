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

import android.telecom.Connection.VideoProvider;
import android.telecom.VideoProfile;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.dialer.util.PermissionsUtil;

import java.lang.reflect.*;

import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.internal.IExtTelephony;
import org.codeaurora.ims.utils.QtiImsExtUtils;

import com.android.ims.ImsManager;
import com.android.incallui.call.DialerCall;

/**
 * This class contains Qti specific utiltity functions.
 */
public class QtiCallUtils {

    private static String LOG_TAG = "QtiCallUtils";
    private static IExtTelephony sIExtTelephony = null;

    /**
     * Returns IExtTelephony handle
     */
    public static IExtTelephony getIExtTelephony() {
        if (sIExtTelephony != null) {
            return sIExtTelephony;
        }
        try {
            Class c = Class.forName("android.os.ServiceManager");
            Method m = c.getMethod("getService",new Class[]{String.class});

            sIExtTelephony =
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
        return sIExtTelephony;
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
        if (!PermissionsUtil.hasPhonePermissions(context)) {
            return false;
        }
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
        if (!PermissionsUtil.hasPhonePermissions(context)) {
            return false;
        }
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

   /**
     * Checks the boolean flag in config file to figure out if we are going to use Qti extension or
     * not
     */
    public static boolean useExt(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        return context != null && context.getResources().getBoolean(R.bool.video_call_use_ext);
    }

    /**
     * Converts the call type to string
     */
    public static String callTypeToString(int callType) {
        switch (callType) {
            case VideoProfile.STATE_BIDIRECTIONAL:
                return "VT";
            case VideoProfile.STATE_TX_ENABLED:
                return "VT_TX";
            case VideoProfile.STATE_RX_ENABLED:
                return "VT_RX";
        }
        return "";
    }

    public static boolean isVideoBidirectional(DialerCall call) {
        return (call != null && call.getVideoState() == VideoProfile.STATE_BIDIRECTIONAL);
    }


    public static boolean isVideoTxOnly(DialerCall call) {
        return (call != null && call.getVideoState() == VideoProfile.STATE_TX_ENABLED);
    }

    public static boolean isVideoRxOnly(DialerCall call) {
        return (call != null && call.getVideoState() == VideoProfile.STATE_RX_ENABLED);
    }

    /**
     * Returns true if the CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO is set to false.
     * Note that - CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL and
     * CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE maps to
     * CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO
     */
    public static boolean hasVoiceCapabilities(DialerCall call) {
        return call != null &&
                !call.can(android.telecom.Call.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
    }

    /**
     * Returns true if local has the VT Transmit and if remote capability has VT Receive set i.e.
     * Local can transmit and remote can receive
     */
    public static boolean hasTransmitVideoCapabilities(DialerCall call) {
        return call != null &&
                call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_RX);
    }

    /**
     * Returns true if local has the VT Receive and if remote capability has VT Transmit set i.e.
     * Local can transmit and remote can receive
     */
    public static boolean hasReceiveVideoCapabilities(DialerCall call) {
        return call != null &&
                call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_RX)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_TX);
    }

    /**
     * Returns true if both voice and video capabilities (see above) are set
     */
    public static boolean hasVoiceOrVideoCapabilities(DialerCall call) {
        return hasVoiceCapabilities(call) || hasTransmitVideoCapabilities(call)
                || hasReceiveVideoCapabilities(call);
    }

    /**
     * Displays the string corresponding to the resourceId as a Toast on the UI
     */
    public static void displayToast(Context context, int resourceId) {
      displayToast(context, context.getResources().getString(resourceId));
    }

    /**
     * Displays the message as a Toast on the UI
     */
    public static void displayToast(Context context, String msg) {
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Returns the call session resource id given the call session event
     */
    public static int getCallSessionEventResId(int event) {
        switch (event) {
            case VideoProvider.SESSION_EVENT_RX_PAUSE:
                return R.string.player_stopped;
            case VideoProvider.SESSION_EVENT_RX_RESUME:
                return R.string.player_started;
            case VideoProvider.SESSION_EVENT_CAMERA_FAILURE:
                return R.string.camera_not_ready;
            case VideoProvider.SESSION_EVENT_CAMERA_READY:
                return R.string.camera_ready;
            default:
                return R.string.unknown_call_session_event;
        }
    }
}
