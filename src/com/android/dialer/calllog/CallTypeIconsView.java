/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.calllog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.BitmapUtil;
import com.android.dialer.R;
import com.android.dialer.EnrichedCallHandler;
import com.android.dialer.util.AppCompatConstants;
import com.google.common.collect.Lists;

import java.util.List;

import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. As this view doesn't create subviews, it is better suited
 * for ListView-recycling that a regular LinearLayout using ImageViews.
 */
public class CallTypeIconsView extends View {
    private List<Integer> mCallTypes = Lists.newArrayListWithCapacity(3);
    private boolean mShowVideo = false;
    private boolean mShowRcs = false;
    private int mWidth;
    private int mHeight;

    private static Resources sResources;

    private static boolean mIsCarrierOneSupported = false;

    public CallTypeIconsView(Context context) {
        this(context, null);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsCarrierOneSupported = QtiImsExtUtils.isCarrierOneSupported();
        if (sResources == null) {
          sResources = new Resources(context);
        }
    }

    public void clear() {
        mCallTypes.clear();
        mWidth = 0;
        mHeight = 0;
        invalidate();
    }

    public void add(int callType) {
        mCallTypes.add(callType);

        final Drawable drawable = getCallTypeDrawable(callType);
        mWidth += drawable.getIntrinsicWidth() + sResources.iconMargin;
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        invalidate();
    }

    public void addImsIcon(int callType, boolean showVideo) {
        mShowVideo = showVideo;
        final Drawable drawable = getLteOrWifiDrawable(callType, showVideo);
        if (drawable != null) {
            // calculating drawable's width and adding it to total width for correct position
            // of icon.
            // calculating height by max of drawable height and other icons' height.
            mWidth += drawable.getIntrinsicWidth();
            mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
            invalidate();
        }
    }

    private Drawable getLteOrWifiDrawable(int callType, boolean showVideo) {
        switch(callType) {
            case AppCompatConstants.INCOMING_IMS_TYPE:
            case AppCompatConstants.OUTGOING_IMS_TYPE:
            case AppCompatConstants.MISSED_IMS_TYPE:
                if (showVideo) {
                    return sResources.vilteCall;
                } else {
                    return sResources.volteCall;
                }
            case AppCompatConstants.INCOMING_WIFI_TYPE:
            case AppCompatConstants.OUTGOING_WIFI_TYPE:
            case AppCompatConstants.MISSED_WIFI_TYPE:
                if (showVideo) {
                    return sResources.viwifiCall;
                } else {
                    return sResources.vowifiCall;
                }
            default:
                return null;
        }
    }

    /**
     * Determines whether the video call icon will be shown.
     *
     * @param showVideo True where the video icon should be shown.
     */
    public void setShowVideo(boolean showVideo) {
        mShowVideo = showVideo;
        if (mIsCarrierOneSupported) {
            //  Don't show video icon in call log item. For CarrierOne, show more precise icon
            //  based on call type in call detail history.
            return;
        }

        if (showVideo) {
            mWidth += sResources.videoCall.getIntrinsicWidth();
            mHeight = Math.max(mHeight, sResources.videoCall.getIntrinsicHeight());
            invalidate();
        }
    }

    /**
     * Determines whether the RCS call icon will be shown.
     *
     * @param mShowRcs True where the RCS icon should be shown.
     */
    public void setShowRcs(boolean showRcs) {
        mShowRcs = showRcs;

        if (showRcs) {
            mWidth += sResources.rcsCall.getIntrinsicWidth();
            mHeight = Math.max(mHeight, sResources.rcsCall.getIntrinsicHeight());
            invalidate();
        }
    }

    /**
     * Determines if the video icon should be shown.
     *
     * @return True if the video icon should be shown.
     */
    public boolean isVideoShown() {
        return mShowVideo;
    }

    @NeededForTesting
    public int getCount() {
        return mCallTypes.size();
    }

    @NeededForTesting
    public int getCallType(int index) {
        return mCallTypes.get(index);
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case AppCompatConstants.CALLS_INCOMING_TYPE:
            case AppCompatConstants.INCOMING_IMS_TYPE:
            case AppCompatConstants.INCOMING_WIFI_TYPE:
                return sResources.incoming;
            case AppCompatConstants.CALLS_OUTGOING_TYPE:
            case AppCompatConstants.OUTGOING_IMS_TYPE:
            case AppCompatConstants.OUTGOING_WIFI_TYPE:
                return sResources.outgoing;
            case AppCompatConstants.CALLS_MISSED_TYPE:
            case AppCompatConstants.MISSED_IMS_TYPE:
            case AppCompatConstants.MISSED_WIFI_TYPE:
                return sResources.missed;
            case AppCompatConstants.CALLS_VOICEMAIL_TYPE:
                return sResources.voicemail;
            case AppCompatConstants.CALLS_BLOCKED_TYPE:
                return sResources.blocked;
            default:
                // It is possible for users to end up with calls with unknown call types in their
                // call history, possibly due to 3rd party call log implementations (e.g. to
                // distinguish between rejected and missed calls). Instead of crashing, just
                // assume that all unknown call types are missed calls.
                return sResources.missed;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = 0;
        for (Integer callType : mCallTypes) {
            final Drawable drawable = getCallTypeDrawable(callType);
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + sResources.iconMargin;
        }

        // If showing the video call icon, draw it scaled appropriately.
        if (!mIsCarrierOneSupported && mShowVideo) {
            final Drawable drawable = sResources.videoCall;
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + sResources.iconMargin;
        }

        // If showing the RCS call icon, draw it scaled appropriately.
        if (mShowRcs && sResources.rcsCall!=null) {
            final Drawable drawable = sResources.rcsCall;
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + sResources.iconMargin;
        }

        for (Integer callType : mCallTypes) {
            final Drawable drawableIms = getLteOrWifiDrawable(callType, mShowVideo);
            if (drawableIms != null) {
                final int right = left + drawableIms.getIntrinsicWidth();
                drawableIms.setBounds(left, 0, right, drawableIms.getIntrinsicHeight());
                drawableIms.draw(canvas);
            }
        }
    }

    private static class Resources {

        // Drawable representing an incoming answered call.
        public final Drawable incoming;

        // Drawable respresenting an outgoing call.
        public final Drawable outgoing;

        // Drawable representing an incoming missed call.
        public final Drawable missed;

        // Drawable representing a voicemail.
        public final Drawable voicemail;

        // Drawable representing a blocked call.
        public final Drawable blocked;

        //  Drawable repesenting a video call.
        public final Drawable videoCall;

        /**
         * Drawable repesenting a RCS call.
         */
        public final Drawable rcsCall;

        /**
         * The margin to use for icons.
         */
        public final int iconMargin;

        /**
         * Drawable repesenting a VoWiFi call.
         */
        public final Drawable vowifiCall;

        /**
         * Drawable repesenting a ViWiFi call.
         */
        public final Drawable viwifiCall;

        /**
         * Drawable repesenting a VoLTE call.
         */
        public final Drawable volteCall;

        /**
         * Drawable repesenting a ViLTE call.
         */
        public final Drawable vilteCall;

        /**
         * Configures the call icon drawables.
         * A single white call arrow which points down and left is used as a basis for all of the
         * call arrow icons, applying rotation and colors as needed.
         *
         * @param context The current context.
         */
        public Resources(Context context) {
            final android.content.res.Resources r = context.getResources();

            incoming = r.getDrawable(R.drawable.ic_call_arrow);
            incoming.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

            // Create a rotated instance of the call arrow for outgoing calls.
            outgoing = BitmapUtil.getRotatedDrawable(r, R.drawable.ic_call_arrow, 180f);
            outgoing.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

            // Need to make a copy of the arrow drawable, otherwise the same instance colored
            // above will be recolored here.
            missed = r.getDrawable(R.drawable.ic_call_arrow).mutate();
            missed.setColorFilter(r.getColor(R.color.missed_call), PorterDuff.Mode.MULTIPLY);

            voicemail = r.getDrawable(R.drawable.ic_call_voicemail_holo_dark);

            blocked = getScaledBitmap(context, R.drawable.ic_block_24dp);
            blocked.setColorFilter(r.getColor(R.color.blocked_call), PorterDuff.Mode.MULTIPLY);

            // Get the video call icon, scaled to match the height of the call arrows.
            // We want the video call icon to be the same height as the call arrows, while keeping
            // the same width aspect ratio.
                videoCall = getScaledBitmap(context, R.drawable.ic_videocam_24dp);
            videoCall.setColorFilter(r.getColor(R.color.dialtacts_secondary_text_color),
                    PorterDuff.Mode.MULTIPLY);

            if (EnrichedCallHandler.getInstance().isRcsFeatureEnabled()) {
                rcsCall = r.getDrawable(R.drawable.ic_dialer_sip_white_24dp).mutate();
                rcsCall.setColorFilter(r.getColor(R.color.dialtacts_secondary_text_color),
                        PorterDuff.Mode.MULTIPLY);
            } else {
                rcsCall = null;
            }

            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);

            viwifiCall = r.getDrawable(R.drawable.viwifi);
            vowifiCall = r.getDrawable(R.drawable.vowifi);
            volteCall = r.getDrawable(R.drawable.volte);
            vilteCall = r.getDrawable(R.drawable.vilte);
        }

        // Gets the icon, scaled to the height of the call type icons. This helps display all the
        // icons to be the same height, while preserving their width aspect ratio.
        private Drawable getScaledBitmap(Context context, int resourceId) {
            Bitmap icon = BitmapFactory.decodeResource(context.getResources(), resourceId);
            int scaledHeight =
                    context.getResources().getDimensionPixelSize(R.dimen.call_type_icon_size);
            int scaledWidth = (int) ((float) icon.getWidth()
                    * ((float) scaledHeight / (float) icon.getHeight()));
            Bitmap scaledIcon = Bitmap.createScaledBitmap(icon, scaledWidth, scaledHeight, false);
            return new BitmapDrawable(context.getResources(), scaledIcon);
        }
    }
}
