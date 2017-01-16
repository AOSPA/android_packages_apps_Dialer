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

package com.android.dialer.list;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.dialer.R;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.EnrichedCallHandler;
import com.android.incallui.Call.LogState;

import org.codeaurora.rcscommon.RcsManager;

/**
 * A helper class for enriched call search list
 * This class will help in creating a rich call entry
 * and enabling/disabling it in the search list.
 */
public class EnrichedSearchAdapterHelper implements
        EnrichedCallHandler.DialtactsActivityListener {

    private Context mContext;
    private int mButtonBGResource = 0;
    private View mRichCallGroup;
    private View mRichCallRootView;
    private ContactListItemView mRichCallItem;
    private ProgressBar mRcsCheckProgressBar;
    private OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener;
    private DialerPhoneNumberListAdapter mListAdapter;
    private EnrichedSearchActionHelper mEnrichedSearchActionHelper;
    private EnrichedCallHandler mEnrichedCallHandler;

    /**
     * Interface to be implemented by the Search list adapter
     */
    public interface EnrichedSearchActionHelper {
        void processDialIntent(int position, long id);
        boolean checkForProhibitedPhoneNumber(String number);
        String getQueryString();
    }

    public EnrichedSearchAdapterHelper(Context context,
            DialerPhoneNumberListAdapter adapter) {
        mContext = context;
        mListAdapter = adapter;
        int[] attrs = new int[] { android.R.attr.selectableItemBackgroundBorderless };
        TypedArray typedArray = context.obtainStyledAttributes(attrs);
        mButtonBGResource = typedArray.getResourceId(0, 0);
        typedArray.recycle();
        mEnrichedCallHandler = EnrichedCallHandler.getInstance();
    }

    /**
     * Handler to handle Enriched call capability check
     */
    private Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EnrichedCallHandler.MSG_ENABLE_RCS:
                setRcsCapability(true);
                break;
            case EnrichedCallHandler.MSG_DISABLE_RCS:
                setRcsCapability(false);
                break;
            case EnrichedCallHandler.MSG_ENABLE_RCS_CHECK_PROGRESS:
                changeRcsCheckProgress(true);
                break;
            case EnrichedCallHandler.MSG_DISABLE_RCS_CHECK_PROGRESS:
                changeRcsCheckProgress(false);
                break;
            case EnrichedCallHandler.MSG_NETWORK_FAILURE:
                showFailureMessage();
                break;
            default:
                // do nothing
            }
        }
    };

    /**
     * set the attributes for the dial button in enriched search list
     * @param ContactListItemView view
     * @param int position
     */
    public void setDialButtonAttributes(ContactListItemView view, int position) {
        view.setDialButton(
                mContext.getResources().getDrawable(
                        R.drawable.ic_dialer_fork_current_call), true);
        view.getDialButton().setBackgroundResource(mButtonBGResource);
        view.getDialButton().setBackgroundTintList(
                mContext.getResources().getColorStateList(
                        R.color.call_log_list_item_primary_action_icon_tint));
        view.getDialButton().setOnClickListener(dialButtonClickListener);
        view.getDialButton().setTag(new Integer(position));
    }

    private View.OnClickListener dialButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            int position = Integer.parseInt(v.getTag().toString());
            mEnrichedSearchActionHelper.processDialIntent(position, -1);
        }
    };

    /**
     * Interface implementation set by the adapter
     * @param DialerPhoneNumberListAdapterActionListener listener
     */
    public void setEnrichedSearchActionHelper(
            EnrichedSearchActionHelper listener) {
        mEnrichedSearchActionHelper = listener;
    }

    /**
     * Callback implementation set the adapter
     * @param OnPhoneNumberPickerActionListener listener
     */
    public void setNumberPickerListener(
            OnPhoneNumberPickerActionListener listener) {
        this.mPhoneNumberPickerActionListener = listener;
    }

    /**
     * Create the rich call entry row
     * @return enriched call view entry
     */
    public View createRichCallEntry() {
        if (mRichCallRootView != null) {
            return mRichCallRootView;
        }
        mRichCallRootView = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.shortcut_list_item_rich_call_enabled, null);
        mRichCallGroup = mRichCallRootView
                .findViewById(R.id.rich_call_search_group);
        mRcsCheckProgressBar = (ProgressBar) mRichCallGroup
                .findViewById(R.id.richfetchserachprogressBar);
        mRichCallItem = (ContactListItemView) mRichCallRootView
                .findViewById(R.id.shortcut_rich_call);
        mRichCallItem.getPhotoView().setVisibility(View.VISIBLE);
        setRcsCapability(EnrichedCallHandler.getInstance()
                .isEnrichedCallCapable());
        mRichCallItem.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                EnrichedCallHandler.getInstance().initializeRcsManager();
                EnrichedCallHandler.getInstance().getRcsManager()
                        .makeEnrichedCall(mEnrichedSearchActionHelper.getQueryString(),
                                new org.codeaurora.rcscommon.NewCallComposerCallback.Stub() {
                                    public void onNewCallComposer(
                                            org.codeaurora.rcscommon.CallComposerData data) {
                                        final Intent intent;
                                        if (data != null && data.isValid()) {
                                            intent = CallUtil.getCallIntent(data
                                                    .getPhoneNumber());
                                            intent.putExtra(RcsManager.ENRICH_CALL_INTENT_EXTRA,
                                                    data);
                                        } else {
                                            mHandler.obtainMessage(EnrichedCallHandler
                                                    .MSG_NETWORK_FAILURE).sendToTarget();
                                            intent = CallUtil.getCallIntent(
                                                    mEnrichedSearchActionHelper.getQueryString());
                                        }
                                        mContext.startActivity(intent);
                                    }
                                }, SubscriptionManager.getDefaultVoiceSubscriptionId());
            }
        });
        assignAttrs(mRichCallItem, R.drawable.ic_rcs_call_black_24dp,
                R.string.call_log_action_rich_call);
        return mRichCallRootView;
    }

    /**
     * Assign attributes to the search list item
     * @param ContactListItemView v
     * @param int drawableId
     * @param int stringId
     */
    private void assignAttrs(ContactListItemView v, int drawableId, int stringId) {
        v.setDrawableResource(drawableId);
        v.setDisplayName(mContext.getResources().getString(stringId));
        v.setPhotoPosition(mListAdapter.getPhotoPosition());
        v.setAdjustSelectionBoundsEnabled(false);
    }

    /**
     * Set the RCS capability after the fetch is complete
     * @param boolean isCapable
     */
    private void setRcsCapability(boolean isCapable) {
        if (mRichCallItem != null) {
            mRichCallItem.setVisibility(View.VISIBLE);
            mRichCallItem.getPhotoView().setVisibility(View.VISIBLE);
            mRcsCheckProgressBar.setVisibility(View.GONE);
            mRichCallItem.setEnabled(isCapable);
            mRichCallItem.setAlpha(isCapable ? 1.0f : 0.3f);
        }
    }

    /**
     * Disable the Enriched call capability check after the timeout
     */
    private void delayedDisableRcsCheckWhenTimedOut() {
        if (mRichCallItem != null) {
            mRichCallItem.postDelayed(mRcsDisableRunnable,
                    EnrichedCallHandler.RCS_CHECK_TIMEOUT);
        }
    }

    private Runnable mRcsDisableRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRichCallItem != null) {
                if (mRcsCheckProgressBar != null
                        && mRcsCheckProgressBar.getVisibility() == View.VISIBLE) {
                    changeRcsCheckProgress(false);
                }
            }
        }
    };

    /**
     * Make changes to the Enriched call list item about the capability check
     * status
     * @param boolean enable
     */
    private void changeRcsCheckProgress(boolean enable) {
        if (mRichCallItem != null) {
            mRichCallItem.setVisibility(View.VISIBLE);
            mRichCallItem.setAlpha(1.0f);
            if (enable) {
                mRichCallItem.getPhotoView().setVisibility(View.GONE);
                mRcsCheckProgressBar.setVisibility(View.VISIBLE);
                mRichCallItem.setEnabled(false);
                delayedDisableRcsCheckWhenTimedOut();
            } else {
                mRichCallItem.setEnabled(true);
                mRichCallItem.getPhotoView().setVisibility(View.VISIBLE);
                mRcsCheckProgressBar.setVisibility(View.GONE);
                mRichCallItem.removeCallbacks(mRcsDisableRunnable);
            }
        }
    }

    /**
     * Show a toast message about RCS service unavailability, when mobile data is not present
     */
    private void showFailureMessage() {
        ConnectivityManager cm = (ConnectivityManager)mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if ((activeNetwork == null) ||
                (activeNetwork.getType() != ConnectivityManager.TYPE_MOBILE)) {
            Toast toast = Toast.makeText(mContext.getApplicationContext(),
                    R.string.rcs_service_unavailable, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }
    }

    /**
     * Called by the dialer activity when the search list scroll state changes
     */
    @Override
    public void onListFragmentScrollStateChange() {
        if (mEnrichedCallHandler.isEnrichedCallCapable()) {
            mHandler.obtainMessage(
                    EnrichedCallHandler.MSG_ENABLE_RCS_CHECK_PROGRESS)
                    .sendToTarget();
            initilizeAndPerformRcsCheck();
        } else {
            mHandler.obtainMessage(EnrichedCallHandler.MSG_DISABLE_RCS)
                    .sendToTarget();
        }
    }

    /**
     * Called by the dialer activity when the dialpad is hidden
     */
    @Override
    public void onHideDialpadFragment() {
        if (mEnrichedCallHandler.isEnrichedCallCapable()) {
            mHandler.obtainMessage(
                    EnrichedCallHandler.MSG_ENABLE_RCS_CHECK_PROGRESS)
                    .sendToTarget();
            initilizeAndPerformRcsCheck();
        } else {
            mHandler.obtainMessage(EnrichedCallHandler.MSG_DISABLE_RCS)
                    .sendToTarget();
        }
    }

    /**
     * Called by the dialer activity when the dialpad is shown
     */
    @Override
    public void onShowDialpadFragment() {
        if (mEnrichedCallHandler.isEnrichedCallCapable()) {
            mHandler.obtainMessage(
                    EnrichedCallHandler.MSG_DISABLE_RCS_CHECK_PROGRESS)
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
            mHandler.obtainMessage(
                    isCapable ? EnrichedCallHandler.MSG_ENABLE_RCS
                            : EnrichedCallHandler.MSG_DISABLE_RCS)
                    .sendToTarget();
        }
    };

    /**
     * Do Enrich call capability check
     */
    private void initilizeAndPerformRcsCheck() {
        mEnrichedCallHandler.initializeRcsManager();
        mEnrichedCallHandler.getRcsManager().fetchEnrichedCallCapabilities(
                mEnrichedSearchActionHelper.getQueryString(), mRcsCallback,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
    }
}
