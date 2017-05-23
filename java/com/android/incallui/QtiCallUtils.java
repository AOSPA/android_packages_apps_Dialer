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

import android.os.IBinder;
import android.os.RemoteException;
import java.lang.reflect.*;
import org.codeaurora.internal.IExtTelephony;

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
}
