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

import android.app.AlertDialog;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.util.TypedValue ;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.WindowManager;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.codeaurora.rcscommon.CallComposerData;

/**
 * RcsCallFragment class is used to display the RCS call content on the incall screen.
 */
public class RcsCallFragment extends
        BaseFragment<RcsCallPresenter, RcsCallPresenter.RcsCallUi> implements
        RcsCallPresenter.RcsCallUi, View.OnClickListener {

    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;
    private static final String PREFERENCE_SHOWN_INFO_DLG_KEY = "RCS_SHOWN_INFO_DLG";

    private TextView mPriorityView, mSubject;
    private ImageView mMapView, mImageView;
    private View mRootView = null, mEnrichDetailLayout;
    private ImageView mExpandCollapseBtn, mRingAnimView;
    private boolean mIsDataAvailable;
    private boolean mIsPresmissionReqPending;
    private static RcsCallPresenter sRcsCallPresenter;

    private RcsCallPresenter.ImageBitmapListener mImageViewListener
            = new RcsCallPresenter.ImageBitmapListener() {
        public void onFetchBitmap(Bitmap bitmap) {
            mImageView.setImageBitmap(bitmap);
        }
    };

    private RcsCallPresenter.ImageBitmapListener mMapViewListener
            = new RcsCallPresenter.ImageBitmapListener() {
        public void onFetchBitmap(Bitmap bitmap) {
            mMapView.setImageBitmap(bitmap);
        }
    };

    @Override
    public RcsCallPresenter createPresenter() {
        sRcsCallPresenter = new RcsCallPresenter();
        return sRcsCallPresenter;
    }

    /**
     * getPresenterInstance will return a singleton instance of RcsCallPresenter
     *
     * @return RcsCallPresenter
     */
    public static RcsCallPresenter getPresenterInstance() {
        return sRcsCallPresenter;
    }

    @Override
    public RcsCallPresenter.RcsCallUi getUi() {
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(this, "onCreate : ");
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Log.d(this, "onCreateView : ");
        mRootView = inflater.inflate(R.layout.enrich_call_content,
                container, false);

        mPriorityView = (TextView) mRootView.findViewById(R.id.call_type);

        mMapView = (ImageView) mRootView.findViewById(R.id.map);
        mMapView.setOnClickListener(this);

        mImageView = (ImageView) mRootView.findViewById(R.id.picture);
        mImageView.setOnClickListener(this);

        mSubject = (TextView) mRootView.findViewById(R.id.notes);
        mSubject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDetailedNotes(mSubject.getText().toString());
            }
        });

        mExpandCollapseBtn= (ImageView) mRootView.findViewById(R.id.expand_collapse);

        mRingAnimView = (ImageView) mRootView.findViewById(R.id.ring_anim);
        mRingAnimView.setVisibility(View.INVISIBLE);

        mEnrichDetailLayout = (LinearLayout) mRootView
                .findViewById(R.id.enrich_details);
        mExpandCollapseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                animate(mEnrichDetailLayout, (ImageView) view, mRingAnimView,
                    !(mEnrichDetailLayout.getVisibility() == View.VISIBLE));
            }
        });

        return mRootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(this, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        Log.d(this, "onResume");
        super.onResume();
    }

    @Override
    public void onClick(View view) {
        Log.d(this,"onClick : " + view);
        switch(view.getId()) {
            case R.id.picture:
                Uri uri = (Uri) mImageView.getTag();
                if(uri != null){
                    getPresenter().onSharedImageClicked(uri);
                }
                break;
            case R.id.map:
                double[] arr = (double[]) mMapView.getTag();
                if(arr != null && arr.length == 2) {
                    getPresenter().onMapImageClicked(arr[0], arr[1]);
                }
                break;
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void updateCallComposerData(RcsCallInfo info) {
        CallComposerData data = info != null ? info.getCallComposerData() : null;
        Log.d(this, "updateCallComposerData : " + data);
        if (data == null) {
            return;
        }
        mIsDataAvailable = false;

        //EnrichCall Subject View update
        if (TextUtils.isEmpty(data.getSubject())) {
            mSubject.setVisibility(View.GONE);
        } else {
            mIsDataAvailable = true;
            mSubject.setVisibility(View.VISIBLE);
        }
        mSubject.setText(data.getSubject());

        //EnrichCall priority view update
        if (data.getPriority() == CallComposerData.PRIORITY.NORMAL) {
            mPriorityView.setBackgroundResource(R.drawable.normal_call_bg);
            mPriorityView.setText(getString(R.string.normal_enrich_call));
        } else {
            mPriorityView.setBackgroundResource(R.drawable.urgent_call_bg);
            mPriorityView.setText(getString(R.string.urgent_enrich_call));
        }

        //Location View update
        if (data.isValidLocation()) {
            mIsDataAvailable = true;
            mMapView.setVisibility(View.VISIBLE);
        } else {
            mMapView.setVisibility(View.GONE);
        }

        if (info.getLocationImageArray() != null
                && info.getLocationImageArray().length > 0) {
            updateLocationImage(info.getLocationImageArray(), data.getLatitude(),
                    data.getLongitude());
        }

        boolean canReadExternalStorage = hasReadStoragePermission();
        Log.d(this, "canReadExternalStorage : " + canReadExternalStorage);

        //SharedImage view update
        if (data.isValidSharedImageUri()) {
            mIsDataAvailable = true;
            mImageView.setVisibility(View.VISIBLE);
        } else {
            mImageView.setVisibility(View.GONE);
        }
        if (canReadExternalStorage && data.isValidSharedImageUri()) {
            getPresenter().updateImageFromUri(data.getImageUri(), mImageViewListener);
            mImageView.setTag(data.getImageUri());
        }

        if (!mIsDataAvailable) {
            mExpandCollapseBtn.setVisibility(View.GONE);
        } else {
            mExpandCollapseBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getMapImageViewWidth() {
        return mMapView != null ? mMapView.getWidth() : 0;
    }

    @Override
    public int getMapImageViewHeight() {
        //consider showing map for max height as when call is answered we need to show
        //full map image.
        return (int) getResources().
                getDimension(R.dimen.expanded_view_picture_height);
    }

    @Override
    public void setEnabled(boolean enabled) {
        Log.d(this, "setEnabled : " + enabled);
        if (mRootView == null) {
            return;
        }
        mRootView.setEnabled(enabled);
    }

    @Override
    public void setVisible(boolean visible) {
        Log.d(this, "setVisible : " + visible);
        if (mRootView == null) {
            return;
        }
        mRootView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showSmallView(boolean showSmall) {
        Log.d(this, "showSmallView : " + showSmall);
        if (mRootView == null) {
            return;
        }
        setMiniUi(mEnrichDetailLayout, showSmall);
    }

    @Override
    public void showEnrichCallFailed(CallComposerData.PRIORITY priority) {
        if (priority == CallComposerData.PRIORITY.HIGH) {
            Toast.makeText(getActivity(), R.string.urgent_call_failed,
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getActivity(), R.string.rich_call_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void showEnrichCallDetail() {
        if (!(mEnrichDetailLayout.getVisibility() == View.VISIBLE)) {
            expandCollapse(mEnrichDetailLayout, mExpandCollapseBtn, mRingAnimView, true);
        }
    }

    @Override
    public void hideEnrichCallDetail() {
        if (mEnrichDetailLayout.getVisibility() == View.VISIBLE) {
            expandCollapse(mEnrichDetailLayout, mExpandCollapseBtn, mRingAnimView, false);
        }
    }

    private void updateLocationImage(final byte[] arr, double lat, double lon) {
        Log.d(this, "updateLocationImage ");
        mMapView.setTag(new double[]{lat, lon});
        getPresenter().updateImageFromBytes(arr, mMapViewListener);
    }

    /**
     * Hide and show RCS content detailed view
     *
     * Additionally if detailed view is hidden ring animation will be shown.
     *
     * @param view   parent view
     * @param buttonView  expand/collapse button view
     * @param ringView  view to show ring animation on expand/collapse button
     * @param show  hide/show detailed view with animation
     */
    private void expandCollapse(final View view, final ImageView buttonView,
            final ImageView ringView, boolean show) {
        /* Show a hide animation if view is visible or when the show variable is false */
        if (view.getVisibility() == View.VISIBLE || !show) {
            view.setVisibility(View.GONE);
            buttonView.setImageResource(R.drawable.ic_rcs_expand);
            if ((ringView != null) && mIsDataAvailable) {
                Animation pulse = AnimationUtils.loadAnimation(getActivity(), R.anim.pulse);
                ringView.startAnimation(pulse);
            }
        } else {
            view.setVisibility(View.VISIBLE);
            if (ringView != null) {
                ringView.clearAnimation();
                ringView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Animation function to hide show detailed view.
     *
     * When detailed view is shown Collapse button is shown
     *    when detaild view is hidden Expand button is shown.
     *
     * Additionally if detailed view is hidden ring animation will be shown.
     *
     * @param view   parent view
     * @param buttonView  expand/collapse button view
     * @param ringView  view to show ring animation on expand/collapse button
     * @param show  hide/show detailed view with animation
     */
    private void animate(final View view, final ImageView buttonView,
            final ImageView ringView, boolean show) {
        /* Show a hide animation if view is visible or when the show variable is false */
        if (view.getVisibility() == View.VISIBLE || !show) {

            Animation animation = AnimationUtils.loadAnimation(getActivity(),
                    R.anim.scale_in);
            //Setting animation duration for collapsing detailed view.
            animation.setDuration(getResources()
                    .getInteger(R.integer.collapse_duration));
            animation.setAnimationListener(new Animation.AnimationListener() {

                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    view.setVisibility(View.GONE);
                    buttonView.setImageResource(R.drawable.ic_rcs_expand);
                    if ((ringView != null)
                            && mIsDataAvailable) {
                        Animation pulse = AnimationUtils.loadAnimation(getActivity(),
                                R.anim.pulse);
                        ringView.startAnimation(pulse);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            view.startAnimation(animation);

        } else {

            view.setVisibility(View.VISIBLE);
            Animation animation = AnimationUtils.loadAnimation(getActivity(),
                    R.anim.scale_out);
            //Setting animation duration for expand detailed view.
            animation.setDuration(getResources().getInteger(R.integer.
                    expand_duration));
            animation.setAnimationListener(new Animation.AnimationListener() {

                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    buttonView.setImageResource(R.drawable.ic_rcs_collpase);
                    if (ringView != null) {
                        ringView.clearAnimation();
                        ringView.setVisibility(View.GONE);
                    }

                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            view.startAnimation(animation);
        }


    }

    private void setMiniUi(View parent, boolean isMinimized) {

        int pxMiniViewHeight = (int) getResources().
                getDimension(R.dimen.mini_view_picture_height);
        int pxExpandedViewHeight = (int) getResources().
                getDimension(R.dimen.expanded_view_picture_height);


        ImageView picture = (ImageView) parent.findViewById(R.id.picture);
        ImageView map = (ImageView) parent.findViewById(R.id.map);

        int dimensionInPx = pxExpandedViewHeight;

        if (isMinimized) {
            dimensionInPx = pxMiniViewHeight;
        }

        picture.getLayoutParams().height = dimensionInPx;
        map.getLayoutParams().height = dimensionInPx;

        map.requestLayout();
        picture.requestLayout();

        parent.requestLayout();

    }

    /**
     * showDetailedNotes will be called to display the note message in a alert dialog so that
     * full text can be shown in an dialog message.
     *
     * @param String message to display
     */
    private void showDetailedNotes(String message) {
        AlertDialog alertDialog = null;

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface
                .OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * isInForground function can be used to know if the incall activity is showing or not.
     *
     * @return boolean, true if incall activity is showing else false.
     */
    public boolean isInForground() {
        return InCallPresenter.getInstance().isShowingInCallUi();
    }

    /**
     * hasReadStoragePermission function can be used to know if the access to read storage
     * permission is granted or not.
     *
     * @return boolean, true if permission is granted else false.
     */
    public boolean hasReadStoragePermission() {
        return getContext().checkPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE, Process.myPid(),
                Process.myUid()) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * isPermissionPending function is used to know if request permission is under process of not.
     *
     * @return boolean, true if request permission is pending else false.
     */
    public boolean isPermissionPending() {
        return mIsPresmissionReqPending;
    }

    /**
     * requestReadStoragePermission function is used to initiate the process to show a dialog to
     * user to grant the read strorage permission.
     */
    public synchronized void requestReadStoragePermission() {
        Log.d(this, "requestReadStoragePermission");
        mIsPresmissionReqPending = true;
        boolean canReadExternalStorage = hasReadStoragePermission();
        if (!canReadExternalStorage) {
            if (canShowPermissionInfoDlg()) {
                showAccessPermissionDialog();
            } else {
                requestAskPermission();
            }
        } else {
            Log.d(this, "already permission granted");
            getPresenter().onReadStoragePermission(true);
            mIsPresmissionReqPending = false;
        }
    }

    /**
     * This function will show a pre request permission dialog from Incall application,
     * and this dialog explains hte user that why we need to read storage permission.
     * Once user presses yes in this dialog then it will ask the actual AOSP permission dialog.
     * If user presses No then it will simply ignore.
     *
     * Note: this explanation dialog will be show to the user only 1 time to avoid showing it
     * multiple times.
     */
    private void showAccessPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        builder.setMessage(R.string.access_permission_message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (InCallPresenter.getInstance().isShowingInCallUi()) {
                            requestAskPermission();
                        }
                    }
                }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mIsPresmissionReqPending = false;
                        getPresenter().onReadStoragePermission(false);
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
        shownPremissionInfoDlg();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                int[] grantResults) {
        Log.d(this, "onRequestPermissionsResult");
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                mIsPresmissionReqPending = false;
                getPresenter().onReadStoragePermission(grantResults != null
                        && grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED);
            }
        }
    }

    /**
     * Process AOSP request permission for read external storage permission.
     */
    private void requestAskPermission() {
        requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE},
                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
    }

    /**
     * canShowPermissionInfoDlg function can be use to know if Incall application can show
     * Permission info dialog.
     *
     * @return true if need to display, false if already display before.
     */
    private boolean canShowPermissionInfoDlg() {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        return !sharedPref.getBoolean(PREFERENCE_SHOWN_INFO_DLG_KEY, false);
    }

    /**
     * shownPremissionInfoDlg function is to write the sharedpreference that request permission
     * info dialog is already displayed.
     */
    private void shownPremissionInfoDlg() {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PREFERENCE_SHOWN_INFO_DLG_KEY, true);
        editor.commit();
    }

}
