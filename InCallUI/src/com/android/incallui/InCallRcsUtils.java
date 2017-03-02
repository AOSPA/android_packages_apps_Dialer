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
import android.telecom.Call.Details;
import android.text.style.ForegroundColorSpan;
import android.text.SpannableStringBuilder;

import org.codeaurora.rcscommon.CallComposerData;

/**
 * This class contains Enrich call specific utiltity functions.
 */
public class InCallRcsUtils {

    /**
     * Verify if current call is of enrich call type.
     * Excludes all other call types [video call, video call upgrade request, conference call
     * wifi call]
     *
     * @param call
     * @param context
     * @return
     */
    public static boolean isEnrichCall(Call call, Context context) {

        final boolean isVideoUpgradeRequest =
                call.getSessionModificationState()
                        == Call.SessionModificationState.
                        RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
        RcsCallInfo callInfo = RcsCallHandler.getInstance().getRcsCallInfo(call);
        return callInfo != null && callInfo.isEnrichedCall()
                && !call.hasProperty(Details.PROPERTY_WIFI)
                && !call.isVideoCall(context)
                && !call.isConferenceCall()
                && !isVideoUpgradeRequest;
    }

    /**
     * Get enrich call notification string for HIGH and NORMAL
     * priority enrich call based on different call states
     *
     * @param call
     * @param isIncomingOrWaiting
     * @return
     */
    public static int getEnrichContentString(Call call, boolean isIncomingOrWaiting) {
        int resId = R.string.notification_ongoing_enrich_call_normal_priority;

        RcsCallInfo callInfo = RcsCallHandler.getInstance().getRcsCallInfo(call);
        CallComposerData data = callInfo != null ? callInfo.getCallComposerData() : null;
        if (isIncomingOrWaiting) {
            if (data != null && data.getPriority() == CallComposerData.PRIORITY.HIGH) {
                resId = R.string.notification_incoming_enrich_call_high_priority;
            } else {
                resId = R.string.notification_incoming_enrich_call_normal_priority;
            }
        } else if (call.getState() == Call.State.ONHOLD) {
            if (data != null && data.getPriority() == CallComposerData.PRIORITY.HIGH) {
                resId = R.string.notification_on_hold_enrich_call_high_priority;
            } else {
                resId = R.string.notification_on_hold_enrich_call_normal_priority;
            }

        } else if (Call.State.isDialing(call.getState())) {
            if (data != null && data.getPriority() == CallComposerData.PRIORITY.HIGH) {
                resId = R.string.notification_dialing_enrich_call_high_priority;
            } else {
                resId = R.string.notification_dialing_enrich_call_normal_priority;
            }

        } else {
            if (data != null && data.getPriority() == CallComposerData.PRIORITY.HIGH) {
                resId = R.string.notification_ongoing_enrich_call_high_priority;
            } else {
                resId = R.string.notification_ongoing_enrich_call_normal_priority;
            }
        }

        return resId;
    }

    /**
     * Get coloured notification content string for enrich call based
     * on enrich call priority
     *
     * @param context
     * @param call
     * @param content
     * @return
     */
    public static SpannableStringBuilder getEnrichContentText(Context context, Call call,
                String content) {
        RcsCallInfo callInfo = RcsCallHandler.getInstance().getRcsCallInfo(call);
        CallComposerData data = callInfo != null ? callInfo.getCallComposerData() : null;
        SpannableStringBuilder styleContent =
                new SpannableStringBuilder();
        if (data != null && data.getPriority() == CallComposerData.PRIORITY.HIGH) {
            appendStyled(styleContent, content,
                    new ForegroundColorSpan(context.getResources().
                            getColor(R.color.urgentCallBackground)));
        } else {
            appendStyled(styleContent, content,
                    new ForegroundColorSpan(context.getResources().
                            getColor(R.color.normalCallBackground)));
        }

        return styleContent;
    }

    /**
     * Handle color spans for string
     *
     * Set styles for subparts in a string. Subparts in a string can be of different styles
     * like color, bold, italic etc..
     *
     * @param builder
     * @param str
     * @param spans
     */
    private static void appendStyled(SpannableStringBuilder builder,
                String str, Object... spans) {
        builder.append(str);
        for (Object span : spans) {
            builder.setSpan(span, builder.length() - str.length(), builder.length(), 0);
        }
    }
}
