/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.incall.impl;

import android.app.Activity;
import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.media.AudioManager;
import android.provider.Settings;
import android.telecom.CallAudioState;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.incallui.BottomSheetHelper;
import com.android.incallui.ExtBottomSheetFragment.ExtBottomSheetActionCallback;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.dialer.widget.LockableViewPager;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.hold.OnHoldFragment;
import com.android.incallui.incall.impl.ButtonController.SpeakerButtonController;
import com.android.incallui.incall.impl.InCallButtonGridFragment.OnButtonGridCreatedListener;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonIdsExtension;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.voicemail.impl.SubscriptionInfoHelper;

import java.util.ArrayList;
import java.util.List;

/** Fragment that shows UI for an ongoing voice call. */
public class InCallFragment extends Fragment
    implements InCallScreen,
        InCallButtonUi,
        OnClickListener,
        ExtBottomSheetActionCallback,
        AudioRouteSelectorPresenter,
        OnButtonGridCreatedListener {

  private List<ButtonController> buttonControllers = new ArrayList<>();
  private View endCallButton;
  private InCallPaginator paginator;
  private LockableViewPager pager;
  private InCallPagerAdapter adapter;
  private View moreOptionsMenuButton;
  private ContactGridManager contactGridManager;
  private InCallScreenDelegate inCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private InCallButtonGridFragment inCallButtonGridFragment;
  @Nullable private ButtonChooser buttonChooser;
  private SecondaryInfo savedSecondaryInfo;
  private int voiceNetworkType;
  private int phoneType = TelephonyManager.PHONE_TYPE_NONE;
  private boolean stateRestored;
  private ImageButton mVbButton;
  private AudioManager mAudioManager;
  private TelephonyManager mTelephonyManager;
  private int mTtyMode;
  private boolean mVolumeBoostEnabled;

  private static final int TTY_MODE_OFF = 0;
  private static final int TTY_MODE_HCO = 2;

  private static final String VOLUME_BOOST = "volume_boost";
  private static final String PREFERRED_TTY_MODE = "preferred_tty_mode";

  // Add animation to educate users. If a call has enriched calling attachments then we'll
  // initially show the attachment page. After a delay seconds we'll animate to the button grid.
  private final Handler handler = new Handler();
  private final Runnable pagerRunnable =
      new Runnable() {
        @Override
        public void run() {
          pager.setCurrentItem(adapter.getButtonGridPosition());
        }
      };

  private static boolean isSupportedButton(@InCallButtonIds int id) {
    return id == InCallButtonIds.BUTTON_AUDIO
        || id == InCallButtonIds.BUTTON_MUTE
        || id == InCallButtonIds.BUTTON_DIALPAD
        || id == InCallButtonIds.BUTTON_HOLD
        || id == InCallButtonIds.BUTTON_SWAP
        || id == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO
        || id == InCallButtonIds.BUTTON_ADD_CALL
        || id == InCallButtonIds.BUTTON_MERGE
        || id == InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      inCallButtonUiDelegate.onRestoreInstanceState(savedInstanceState);
      stateRestored = true;
    }
    mAudioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
    mTelephonyManager = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
    mTtyMode = Settings.Secure.getInt(getContext().getContentResolver(),
        PREFERRED_TTY_MODE, TTY_MODE_OFF);
    mVolumeBoostEnabled = mAudioManager.getParameters(VOLUME_BOOST).contains("=on");
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onCreateView", null);
    final View view = layoutInflater.inflate(R.layout.frag_incall_voice, viewGroup, false);
    contactGridManager =
        new ContactGridManager(
            view,
            (ImageView) view.findViewById(R.id.contactgrid_avatar),
            getResources().getDimensionPixelSize(R.dimen.incall_avatar_size),
            true /* showAnonymousAvatar */);

    paginator = (InCallPaginator) view.findViewById(R.id.incall_paginator);
    pager = (LockableViewPager) view.findViewById(R.id.incall_pager);
    pager.setOnTouchListener(
        (v, event) -> {
          handler.removeCallbacks(pagerRunnable);
          return false;
        });

    endCallButton = view.findViewById(R.id.incall_end_call);
    endCallButton.setOnClickListener(this);

    moreOptionsMenuButton = view.findViewById(R.id.incall_more_button);
    moreOptionsMenuButton.setOnClickListener(this);

    mVbButton = (ImageButton) view.findViewById(R.id.volumeBoost);
      if (mVbButton != null) {
        mVbButton.setOnClickListener(new View. OnClickListener() {
          @Override
          public void onClick(View arg0) {
            if (isVbAvailable()) {
            // Switch Volume Boost status
            setVolumeBoost(!isVolumeBoostOn());
            }

            updateVbButton();
            showVbNotify();
          }
      });
      }
      updateVoiceNetworkType();

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    inCallButtonUiDelegate.refreshMuteState();
    inCallScreenDelegate.onInCallScreenResumed();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onViewCreated", null);
    super.onViewCreated(view, bundle);
    inCallScreenDelegate =
        FragmentUtils.getParent(this, InCallScreenDelegateFactory.class).newInCallScreenDelegate();
    Assert.isNotNull(inCallScreenDelegate);

    buttonControllers.add(new ButtonController.MuteButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SpeakerButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.DialpadButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.HoldButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.AddCallButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SwapButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.MergeButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.UpgradeToVideoButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.ManageConferenceButtonController(inCallScreenDelegate));
    buttonControllers.add(
        new ButtonController.SwitchToSecondaryButtonController(inCallScreenDelegate));

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
  }

  @Override
  public void onPause() {
    super.onPause();
    inCallScreenDelegate.onInCallScreenPaused();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    inCallButtonUiDelegate.onSaveInstanceState(outState);
  }

  @Override
  public void onClick(View view) {
    if (view == endCallButton) {
      LogUtil.i("InCallFragment.onClick", "end call button clicked");
      inCallScreenDelegate.onEndCallClicked();
    } else if (view == moreOptionsMenuButton) {
      LogUtil.i("InCallFragment.onClick","more options button clicked");
      BottomSheetHelper.getInstance()
              .showBottomSheet(getChildFragmentManager());
    } else {
      LogUtil.e("InCallFragment.onClick", "unknown view: " + view);
      Assert.fail();
    }
  }

  @Override
  public void optionSelected(@Nullable String text) {
    //Tiggered on item selection on bottomsheet.
    BottomSheetHelper.getInstance().optionSelected(text);
  }

  @Override
  public void sheetDismissed() {
    BottomSheetHelper.getInstance().sheetDismissed();
  }

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("InCallFragment.setPrimary", primaryInfo.toString());
    setAdapterMedia(primaryInfo.multimediaData);
    contactGridManager.setPrimary(primaryInfo);

    if (primaryInfo.shouldShowLocation) {
      // Hide the avatar to make room for location
      contactGridManager.setAvatarHidden(true);

      // Need to widen the contact grid to fit location information
      View contactGridView = getView().findViewById(R.id.incall_contact_grid);
      ViewGroup.LayoutParams params = contactGridView.getLayoutParams();
      if (params instanceof ViewGroup.MarginLayoutParams) {
        ((ViewGroup.MarginLayoutParams) params).setMarginStart(0);
        ((ViewGroup.MarginLayoutParams) params).setMarginEnd(0);
      }
      contactGridView.setLayoutParams(params);

      // Need to let the dialpad move up a little further when location info is being shown
      View dialpadView = getView().findViewById(R.id.incall_dialpad_container);
      params = dialpadView.getLayoutParams();
      if (params instanceof RelativeLayout.LayoutParams) {
        ((RelativeLayout.LayoutParams) params).removeRule(RelativeLayout.BELOW);
      }
      dialpadView.setLayoutParams(params);
    }
  }

  private void setAdapterMedia(MultimediaData multimediaData) {
    if (adapter == null) {
      adapter = new InCallPagerAdapter(getChildFragmentManager(), multimediaData);
      pager.setAdapter(adapter);
    } else {
      adapter.setAttachments(multimediaData);
    }

    if (adapter.getCount() > 1 && getResources().getInteger(R.integer.incall_num_rows) > 1) {
      paginator.setVisibility(View.VISIBLE);
      paginator.setupWithViewPager(pager);
      pager.setSwipingLocked(false);
      if (!stateRestored) {
        handler.postDelayed(pagerRunnable, 4_000);
      } else {
        pager.setCurrentItem(adapter.getButtonGridPosition(), false /* animateScroll */);
      }
    } else {
      paginator.setVisibility(View.GONE);
    }
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("InCallFragment.setSecondary", secondaryInfo.toString());
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setEnabled(secondaryInfo.shouldShow);
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setAllowed(secondaryInfo.shouldShow);
    updateButtonStates();

    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    Fragment oldBanner = getChildFragmentManager().findFragmentById(R.id.incall_on_hold_banner);
    if (secondaryInfo.shouldShow) {
      transaction.replace(R.id.incall_on_hold_banner, OnHoldFragment.newInstance(secondaryInfo));
    } else {
      if (oldBanner != null) {
        transaction.remove(oldBanner);
      }
    }
    transaction.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_out_top);
    transaction.commitAllowingStateLoss();
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("InCallFragment.setCallState", primaryCallState.toString());
    setPhoneType();
    updateVoiceNetworkType();
    contactGridManager.setCallState(primaryCallState);
    buttonChooser =
        ButtonChooserFactory.newButtonChooser(voiceNetworkType, primaryCallState.isWifi, phoneType);
    updateButtonStates();
    if (mVbButton != null) {
      updateVbByCall(primaryCallState.state);
    }
  }

  private void setPhoneType() {
    if (phoneType == TelephonyManager.PHONE_TYPE_NONE) {
      DialerCall activeCall = CallList.getInstance().getFirstCall();
      if (activeCall != null) {
        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(getContext(),
            activeCall.getAccountHandle());
        if (subInfoHelper != null) {
          int subId = subInfoHelper.getSubId();
          phoneType = (subId == SubscriptionInfoHelper.NO_SUB_ID) ?
              TelephonyManager.PHONE_TYPE_SIP :
              mTelephonyManager.getCurrentPhoneType(subId);
        }
      }
    }
  }

  private void updateVoiceNetworkType() {
      if (ContextCompat.checkSelfPermission(getContext(), permission.READ_PHONE_STATE)
              != PackageManager.PERMISSION_GRANTED) {
          voiceNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
      } else {
          voiceNetworkType =
              VERSION.SDK_INT >= VERSION_CODES.N
              ? mTelephonyManager.getVoiceNetworkType()
              : TelephonyManager.NETWORK_TYPE_UNKNOWN;
      }
      LogUtil.v("InCallFragment.updateVoiceNetwork", "NetworkType: " +
                                                        Integer.toString(voiceNetworkType));
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
    if (endCallButton != null) {
      endCallButton.setEnabled(enabled);
    }
  }

  @Override
  public void showManageConferenceCallButton(boolean visible) {
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setAllowed(visible);
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setEnabled(visible);
    updateButtonStates();
  }

  @Override
  public boolean isManageConferenceVisible() {
    return getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).isAllowed();
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    contactGridManager.dispatchPopulateAccessibilityEvent(event);
  }

  @Override
  public void showNoteSentToast() {
    LogUtil.i("InCallFragment.showNoteSentToast", null);
    Toast.makeText(getContext(), R.string.incall_note_sent, Toast.LENGTH_LONG).show();
  }

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    LogUtil.i("InCallFragment.onInCallScreenDialpadVisibilityChange", "isShowing: " + isShowing);
    // Take note that the dialpad button isShowing
    getButtonController(InCallButtonIds.BUTTON_DIALPAD).setChecked(isShowing);

    // This check is needed because there is a race condition where we attempt to update
    // ButtonGridFragment before it is ready, so we check whether it is ready first and once it is
    // ready, #onButtonGridCreated will mark the dialpad button as isShowing.
    if (inCallButtonGridFragment != null) {
      // Update the Android Button's state to isShowing.
      inCallButtonGridFragment.onInCallScreenDialpadVisibilityChange(isShowing);
    }
  }

  @Override
  public void onInCallShowDialpad(boolean isShown) {
    LogUtil.i("InCallFragment.onInCallShowDialpad","isShown: "+isShown);
    BottomSheetHelper bottomSheetHelper = BottomSheetHelper.getInstance();
    bottomSheetHelper.updateMoreButtonVisibility(
        isShown ? false : bottomSheetHelper.shallShowMoreButton(getActivity()),
        moreOptionsMenuButton);
  }

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return R.id.incall_dialpad_container;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void showButton(@InCallButtonIds int buttonId, boolean show) {
    LogUtil.v(
        "InCallFragment.showButton",
        "buttionId: %s, show: %b",
        InCallButtonIdsExtension.toString(buttonId),
        show);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setAllowed(show);
      if (buttonId == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO && show) {
        Logger.get(getContext())
            .logImpression(DialerImpression.Type.UPGRADE_TO_VIDEO_CALL_BUTTON_SHOWN);
      }
    }
  }

  @Override
  public void enableButton(@InCallButtonIds int buttonId, boolean enable) {
    LogUtil.v(
        "InCallFragment.enableButton",
        "buttonId: %s, enable: %b",
        InCallButtonIdsExtension.toString(buttonId),
        enable);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setEnabled(enable);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    LogUtil.v("InCallFragment.setEnabled", "enabled: " + enabled);
    for (ButtonController buttonController : buttonControllers) {
      buttonController.setEnabled(enabled);
    }
  }

  @Override
  public void setHold(boolean value) {
    getButtonController(InCallButtonIds.BUTTON_HOLD).setChecked(value);
  }

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {}

  @Override
  public void setVideoPaused(boolean isPaused) {}

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("InCallFragment.setAudioState", "audioState: " + audioState);
    ((SpeakerButtonController) getButtonController(InCallButtonIds.BUTTON_AUDIO))
        .setAudioState(audioState);
    getButtonController(InCallButtonIds.BUTTON_MUTE).setChecked(audioState.isMuted());
  }

  @Override
  public void updateButtonStates() {
    // When the incall screen is ready, this method is called from #setSecondary, even though the
    // incall button ui is not ready yet. This method is called again once the incall button ui is
    // ready though, so this operation is safe and will be executed asap.
    if (inCallButtonGridFragment == null) {
      return;
    }
    setPhoneType();
    int numVisibleButtons =
        inCallButtonGridFragment.updateButtonStates(
            buttonControllers, buttonChooser, voiceNetworkType, phoneType);

    int visibility = numVisibleButtons == 0 ? View.GONE : View.VISIBLE;
    pager.setVisibility(visibility);
    if (adapter != null
        && adapter.getCount() > 1
        && getResources().getInteger(R.integer.incall_num_rows) > 1) {
      paginator.setVisibility(View.VISIBLE);
      pager.setSwipingLocked(false);
    } else {
      paginator.setVisibility(View.GONE);
      if (adapter != null) {
        pager.setSwipingLocked(true);
        pager.setCurrentItem(adapter.getButtonGridPosition());
      }
    }
    BottomSheetHelper bottomSheetHelper = BottomSheetHelper.getInstance();
    bottomSheetHelper.updateMoreButtonVisibility(
        bottomSheetHelper.shallShowMoreButton(getActivity()), moreOptionsMenuButton);
  }

  @Override
  public void updateInCallButtonUiColors() {}

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.getCurrentAudioState())
        .show(getChildFragmentManager(), null);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    inCallButtonUiDelegate.setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @NonNull
  @Override
  public ButtonController getButtonController(@InCallButtonIds int id) {
    for (ButtonController buttonController : buttonControllers) {
      if (buttonController.getInCallButtonId() == id) {
        return buttonController;
      }
    }
    Assert.fail();
    return null;
  }

  @Override
  public void onButtonGridCreated(InCallButtonGridFragment inCallButtonGridFragment) {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiReady");
    this.inCallButtonGridFragment = inCallButtonGridFragment;
    inCallButtonUiDelegate.onInCallButtonUiReady(this);
    updateButtonStates();
  }

  @Override
  public void onButtonGridDestroyed() {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiUnready");
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    this.inCallButtonGridFragment = null;
  }

  @Override
  public boolean isShowingLocationUi() {
    Fragment fragment = getLocationFragment();
    return fragment != null && fragment.isVisible();
  }

  @Override
  public void showLocationUi(@Nullable Fragment locationUi) {
    boolean isVisible = isShowingLocationUi();
    if (locationUi != null && !isVisible) {
      // Show the location fragment.
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.incall_location_holder, locationUi)
          .commitAllowingStateLoss();
    } else if (locationUi == null && isVisible) {
      // Hide the location fragment
      getChildFragmentManager()
          .beginTransaction()
          .remove(getLocationFragment())
          .commitAllowingStateLoss();
    }
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
    super.onMultiWindowModeChanged(isInMultiWindowMode);
    if (isInMultiWindowMode == isShowingLocationUi()) {
      LogUtil.i("InCallFragment.onMultiWindowModeChanged", "hide = " + isInMultiWindowMode);
      // Need to show or hide location
      showLocationUi(isInMultiWindowMode ? null : getLocationFragment());
    }
  }

  private Fragment getLocationFragment() {
    return getChildFragmentManager().findFragmentById(R.id.incall_location_holder);
  }

  private boolean isVbAvailable() {
    int mode = AudioModeProvider.getInstance().getAudioState().getRoute();

    return (mode == CallAudioState.ROUTE_EARPIECE || mode == CallAudioState.ROUTE_SPEAKER
        || mTtyMode == TTY_MODE_HCO);
  }

  private void updateVbButton() {
    if (mVbButton != null) {
      if (isVbAvailable()) {
        if (isVolumeBoostOn()) {
          mVbButton.setBackgroundResource(R.drawable.vb_active);
        } else {
          mVbButton.setBackgroundResource(R.drawable.vb_normal);
        }
      } else {
        mVbButton.setBackgroundResource(R.drawable.vb_disable);
      }
    }
  }

  @Override
  public void showVbButton(boolean show) {
    if (mVbButton != null){
      mVbButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }
  }

  private void showVbNotify() {
    Toast vbnotify;
    int resId = R.string.volume_boost_notify_unavailable;

    if (isVbAvailable()) {
      if (isVolumeBoostOn()) {
        resId = R.string.volume_boost_notify_enabled;
      } else {
        resId = R.string.volume_boost_notify_disabled;
      }
    }

    vbnotify = Toast.makeText(getView().getContext(), resId, Toast.LENGTH_SHORT);
    vbnotify.setGravity(Gravity.CENTER, 0, 0);
    vbnotify.show();
  }

  private void updateVbByCall(int state) {
    updateVbButton();

    if (DialerCall.State.ACTIVE == state) {
      mVbButton.setVisibility(View.VISIBLE);
    } else {
      mVbButton.setVisibility(View.GONE);
      if (isVolumeBoostOn()) {
        setVolumeBoost(false);
      }
    }
  }

  public void updateVbByAudioMode(CallAudioState audioState) {
    int mode = audioState.getRoute();
    if (!(mode == CallAudioState.ROUTE_EARPIECE
        || mode == CallAudioState.ROUTE_BLUETOOTH
        || mode == CallAudioState.ROUTE_WIRED_HEADSET
        || mode == CallAudioState.ROUTE_SPEAKER)) {
    return;
    }

    if (mAudioManager != null && isVolumeBoostOn()) {
      setVolumeBoost(false);
    }

    updateVbButton();
  }

  private void setVolumeBoost(boolean on){
    if (on) {
      mAudioManager.setParameters(VOLUME_BOOST + "=on");
    } else {
      mAudioManager.setParameters(VOLUME_BOOST + "=off");
    }
    mVolumeBoostEnabled = on;
  }

  private boolean isVolumeBoostOn(){

    return mVolumeBoostEnabled;
  }
}
