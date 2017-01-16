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
package com.android.dialer.calllog;

import com.android.contacts.common.CallUtil;
import com.android.dialer.EnrichedCallHandler;
import com.android.dialer.R;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.codeaurora.rcscommon.RcsManager;

public class EnrichedCallLogListItemHelper implements View.OnClickListener {

    public interface EnrichedGetNumberHelper {
        String getNumber();
    }

    private Context mContext;
    private View mEnrichedCallButtonView;
    private ProgressBar mRichFetchProgressBar;
    private TextView mRichCallText;
    private ImageView mRichCallIcon;
    private EnrichedGetNumberHelper mEnrichedGetNumberHelper;

    public EnrichedCallLogListItemHelper(Context context) {
        mContext = context;
    }

    public void setEnrichedGetNumberHelper(
            EnrichedGetNumberHelper listener) {
        this.mEnrichedGetNumberHelper = listener;
    }

    public void inflateEnrichedItem(ViewGroup root) {
        mEnrichedCallButtonView = root.findViewById(R.id.rich_call_action);
        mEnrichedCallButtonView.setVisibility(View.VISIBLE);
        mRichCallText = (TextView) root
                .findViewById(R.id.rich_call_action_text);
        mRichCallIcon = (ImageView) root.findViewById(R.id.richCallIcon);
        mEnrichedCallButtonView.setOnClickListener(this);
        mEnrichedCallButtonView.setEnabled(false);
        mRichFetchProgressBar = (ProgressBar) mEnrichedCallButtonView
                .findViewById(R.id.richfetchprogressBar);
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == EnrichedCallHandler.MSG_ENABLE_RCS) {
                mRichFetchProgressBar.setVisibility(View.GONE);
                mRichCallIcon.setVisibility(View.VISIBLE);
                mRichCallIcon.setAlpha(1.0f);
                mRichCallText.setAlpha(1.0f);
                mEnrichedCallButtonView.setEnabled(true);
            } else if (msg.what == EnrichedCallHandler.MSG_DISABLE_RCS) {
                if (mRichFetchProgressBar.getVisibility() == View.VISIBLE) {
                    mRichFetchProgressBar.setVisibility(View.GONE);
                    mRichCallIcon.setVisibility(View.VISIBLE);
                    mRichCallIcon.setAlpha(0.3f);
                    mRichCallText.setAlpha(0.3f);
                    mEnrichedCallButtonView.setEnabled(false);
                }
            } else if (msg.what == EnrichedCallHandler.MSG_CHECK_RCS) {
                if (mEnrichedCallButtonView != null) {
                    mEnrichedCallButtonView.setEnabled(false);
                    mRichFetchProgressBar.setVisibility(View.VISIBLE);
                    mRichCallIcon.setVisibility(View.GONE);
                    mRichCallIcon.setAlpha(0.3f);
                    mRichCallText.setAlpha(0.3f);
                    initilizeAndPerformRcsCheck();
                    delayDisableRcsCheckTimedout();
                }
            }
        }
    };

    public void showActions() {
        if (EnrichedCallHandler.getInstance().isEnrichedCallCapable()) {
            mHandler.obtainMessage(EnrichedCallHandler.MSG_CHECK_RCS)
                    .sendToTarget();
        } else {
            mHandler.obtainMessage(EnrichedCallHandler.MSG_DISABLE_RCS)
                    .sendToTarget();
        }
    }

    private final org.codeaurora.rcscommon.RichCallCapabilitiesCallback mRcsCallback =
            new org.codeaurora.rcscommon.RichCallCapabilitiesCallback.Stub() {
        @Override
        public void onRichCallCapabilitiesFetch(boolean isCapable) {
            if (EnrichedCallHandler.getInstance().isEnrichedCallCapable()) {
                mHandler.obtainMessage(
                        isCapable ? EnrichedCallHandler.MSG_ENABLE_RCS
                                : EnrichedCallHandler.MSG_DISABLE_RCS)
                        .sendToTarget();
            }
        }
    };

    private void delayDisableRcsCheckTimedout() {
        mEnrichedCallButtonView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mHandler.obtainMessage(EnrichedCallHandler.MSG_DISABLE_RCS)
                        .sendToTarget();
            }
        }, EnrichedCallHandler.RCS_CHECK_TIMEOUT);
    }

    private void initilizeAndPerformRcsCheck() {
        EnrichedCallHandler.getInstance().initializeRcsManager();
        EnrichedCallHandler.getInstance().getRcsManager()
                .fetchEnrichedCallCapabilities(
                        mEnrichedGetNumberHelper.getNumber(), mRcsCallback,
                        SubscriptionManager.getDefaultVoiceSubscriptionId());
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.rich_call_action) {
            EnrichedCallHandler.getInstance().getRcsManager()
                    .makeEnrichedCall(mEnrichedGetNumberHelper.getNumber(),
                            new org.codeaurora.rcscommon.NewCallComposerCallback.Stub() {
                                public void onNewCallComposer(
                                        org.codeaurora.rcscommon.CallComposerData data) {
                                    final Intent intent;
                                    if (data != null && data.isValid()
                                            && data.getPhoneNumber() != null) {
                                        intent = CallUtil.getCallIntent(data
                                                .getPhoneNumber());
                                        intent.putExtra(
                                                RcsManager.ENRICH_CALL_INTENT_EXTRA,
                                                data);
                                    } else {
                                        intent = CallUtil
                                                .getCallIntent(mEnrichedGetNumberHelper
                                                        .getNumber());
                                    }
                                    mContext.startActivity(intent);
                                }
                            }, SubscriptionManager.getDefaultVoiceSubscriptionId());
        }
    }
}
