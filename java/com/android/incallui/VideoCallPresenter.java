/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;
import android.view.Surface;
import android.view.SurfaceView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallOrientationListener;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.CameraDirection;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.call.InCallVideoCallCallbackNotifier;
import com.android.incallui.call.InCallVideoCallCallbackNotifier.SurfaceChangeListener;
import com.android.incallui.call.InCallVideoCallCallbackNotifier.VideoEventListener;
import com.android.incallui.util.AccessibilityUtil;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.videosurface.protocol.VideoSurfaceDelegate;
import com.android.incallui.videosurface.protocol.VideoSurfaceTexture;
import com.android.incallui.videotech.utils.SessionModificationState;
import com.android.incallui.videotech.utils.VideoUtils;
import java.util.Objects;

import org.codeaurora.ims.utils.QtiImsExtUtils;
/**
 * Logic related to the {@link VideoCallScreen} and for managing changes to the video calling
 * surfaces based on other user interface events and incoming events from the {@class
 * VideoCallListener}.
 *
 * <p>When a call's video state changes to bi-directional video, the {@link
 * com.android.incallui.VideoCallPresenter} performs the following negotiation with the telephony
 * layer:
 *
 * <ul>
 * <li>{@code VideoCallPresenter} creates and informs telephony of the display surface.
 * <li>{@code VideoCallPresenter} creates the preview surface.
 * <li>{@code VideoCallPresenter} informs telephony of the currently selected camera.
 * <li>Telephony layer sends {@link CameraCapabilities}, including the dimensions of the video for
 *     the current camera.
 * <li>{@code VideoCallPresenter} adjusts size of the preview surface to match the aspect ratio of
 *     the camera.
 * <li>{@code VideoCallPresenter} informs telephony of the new preview surface.
 * </ul>
 *
 * <p>When downgrading to an audio-only video state, the {@code VideoCallPresenter} nulls both
 * surfaces.
 */
public class VideoCallPresenter
    implements IncomingCallListener,
        InCallOrientationListener,
        InCallStateListener,
        InCallDetailsListener,
        SurfaceChangeListener,
        InCallPresenter.InCallEventListener,
        VideoCallScreenDelegate,
        InCallUiStateNotifierListener,
        VideoEventListener,
        PictureModeHelper.Listener {

  private static boolean mIsVideoMode = false;

  private final Handler mHandler = new Handler();
  private VideoCallScreen mVideoCallScreen;

  /** The current context. */
  private Context mContext;

  /** The call the video surfaces are currently related to */
  private DialerCall mPrimaryCall;
  /**
   * The {@link VideoCall} used to inform the video telephony layer of changes to the video
   * surfaces.
   */
  private VideoCall mVideoCall;
  /** Determines if the current UI state represents a video call. */
  private int mCurrentVideoState = VideoProfile.STATE_AUDIO_ONLY;
  /** DialerCall's current state */
  private int mCurrentCallState = DialerCall.State.INVALID;
  /** Determines the device orientation (portrait/lanscape). */
  private int mDeviceOrientation = InCallOrientationEventListener.SCREEN_ORIENTATION_UNKNOWN;
  /** Tracks the state of the preview surface negotiation with the telephony layer. */
  private int mPreviewSurfaceState = PreviewSurfaceState.NONE;
  /**
   * Determines whether video calls should automatically enter full screen mode after {@link
   * #mAutoFullscreenTimeoutMillis} milliseconds.
   */
  private boolean mIsAutoFullscreenEnabled = false;
  /**
   * Determines the number of milliseconds after which a video call will automatically enter
   * fullscreen mode. Requires {@link #mIsAutoFullscreenEnabled} to be {@code true}.
   */
  private int mAutoFullscreenTimeoutMillis = 0;
  /**
   * Determines if the countdown is currently running to automatically enter full screen video mode.
   */
  private boolean mAutoFullScreenPending = false;

  /** Stores current orientation mode for primary call.*/
  private int mCurrentOrientationMode = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

  /** Whether if the call is remotely held. */
  private boolean mIsRemotelyHeld = false;

  /**
   * Caches information about whether InCall UI is in the background or foreground
   */
  private boolean mIsInBackground = true;

  // Holds TRUE if default image should be used as static image else holds FALSE
  private static boolean sUseDefaultImage = false;
  // Holds TRUE if static image needs to be transmitted instead of video preview stream
  private static boolean sShallTransmitStaticImage = false;

  /**
   * Cache the size set in the "local_preview_surface_size" settings db property
   */
  private Point mFixedPreviewSurfaceSize;

  private static PictureModeHelper mPictureModeHelper;

  /**
   * Determines if the incoming video is available. If the call session resume event has been
   * received (i.e PLAYER_START has been received from lower layers), incoming video is
   * available. If the call session pause event has been received (i.e PLAYER_STOP has been
   * received from lower layers), incoming video is not available.
   */
  private static boolean mIsIncomingVideoAvailable = false;

  /**
   * Runnable which is posted to schedule automatically entering fullscreen mode. Will not auto
   * enter fullscreen mode if the dialpad is visible (doing so would make it impossible to exit the
   * dialpad).
   */
  private Runnable mAutoFullscreenRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (mAutoFullScreenPending
              && !InCallPresenter.getInstance().isDialpadVisible()
              && mIsVideoMode) {

            LogUtil.v("VideoCallPresenter.mAutoFullScreenRunnable", "entering fullscreen mode");
            InCallPresenter.getInstance().setFullScreen(true);
            mAutoFullScreenPending = false;
          } else {
            LogUtil.v(
                "VideoCallPresenter.mAutoFullScreenRunnable",
                "skipping scheduled fullscreen mode.");
          }
        }
      };

  private boolean isVideoCallScreenUiReady;

  private boolean isCameraRequired(int videoState, int sessionModificationState) {
    return !mIsInBackground && !shallTransmitStaticImage() &&
        !isModifyToVideoRxType(mPrimaryCall) &&
        (VideoProfile.isBidirectional(videoState)
        || VideoProfile.isTransmissionEnabled(videoState)
        || (VideoProfile.isAudioOnly(videoState) && isVideoUpgrade(sessionModificationState)));
  }

  /**
   * Opens camera if the camera has not yet been set on the {@link VideoCall}; negotiation has
   * not yet started and if camera is required
   */
  private void maybeEnableCamera() {
    if (mPreviewSurfaceState == PreviewSurfaceState.NONE && isCameraRequired()) {
      enableCamera(mVideoCall, true);
    }
  }

  /**
   * This method gets invoked when visibility of InCallUI is changed. For eg.
   * when UE moves in/out of the foreground, display either turns ON/OFF
   * @param showing true if InCallUI is visible, false  otherwise.
   */
  @Override
  public void onUiShowing(boolean showing) {
    LogUtil.i("VideoCallPresenter.onUiShowing", " showing = " + showing + " mPrimaryCall = " +
        mPrimaryCall + " mPreviewSurfaceState = " + mPreviewSurfaceState);

    mIsInBackground = !showing;
    boolean wasTransmitStaticImage = sShallTransmitStaticImage;

    int phoneId = BottomSheetHelper.getInstance().getPhoneId();
    if (!QtiImsExtUtils.shallShowStaticImageUi(phoneId, mContext) &&
        QtiImsExtUtils.shallTransmitStaticImage(phoneId, mContext)) {
      sShallTransmitStaticImage = sUseDefaultImage = mIsInBackground;
    }

    if (!isVideoCall(mPrimaryCall) && !isVideoUpgrade(mPrimaryCall)) {
      LogUtil.w("VideoCallPresenter.onUiShowing", " received for voice call");
      if (mPreviewSurfaceState != PreviewSurfaceState.NONE) {
         enableCamera(mVideoCall, false);
      }
      return;
    }

    if (!QtiImsExtUtils.shallShowStaticImageUi(phoneId, mContext) &&
        QtiImsExtUtils.shallTransmitStaticImage(phoneId, mContext) &&
        isTransmissionEnabled(mPrimaryCall) &&
        (showing || isActiveVideoCall(mPrimaryCall))) {
      // Set pause image only for ACTIVE calls going to background.
      // While coming to foreground, unset pause image for all calls.
      setPauseImage();
      if(wasTransmitStaticImage != sShallTransmitStaticImage && mPrimaryCall != null) {
        showVideoUi(
            mPrimaryCall.getVideoState(),
            mPrimaryCall.getState(),
            mPrimaryCall.getVideoTech().getSessionModificationState(),
            mPrimaryCall.isRemotelyHeld());
      }
    }

    if (showing) {
      maybeEnableCamera();
    } else if (mPreviewSurfaceState != PreviewSurfaceState.NONE) {
      checkForOrientationAllowedChange(mPrimaryCall);
      enableCamera(mVideoCall, false);
    }
  }

  private void setPauseImage(VideoCall videoCall) {
    String uriStr = null;
    Uri uri = null;

    LogUtil.d("VideoCallPresenter.setPauseImage"," videoCall = " + videoCall);
    if (!QtiImsExtUtils.shallTransmitStaticImage(
            BottomSheetHelper.getInstance().getPhoneId(), mContext)
        || videoCall == null) {
        return;
    }

    if (shallTransmitStaticImage()) {
        uriStr = sUseDefaultImage ? "" :
            QtiImsExtUtils.getStaticImageUriStr(mContext.getContentResolver());
    }

    uri = uriStr != null ? Uri.parse(uriStr) : null;
    LogUtil.d("VideoCallPresenter.setPauseImage"," parsed uri = " + uri + " sUseDefaultImage = "
        + sUseDefaultImage);
    videoCall.setPauseImage(uri);
  }

  @Override
  public void setPauseImage() {
    setPauseImage(mVideoCall);
  }

  @Override
  public boolean shallTransmitStaticImage() {
    return sShallTransmitStaticImage;
  }

  @Override
  public boolean isUseDefaultImage() {
    return sUseDefaultImage;
  }

  @Override
  public void setUseDefaultImage(boolean useDefaultImage) {
    sUseDefaultImage = useDefaultImage;
  }
  /**
   * Determines if the incoming video surface should be shown based on the current videoState and
   * callState. The video surface is shown when incoming video is not paused, the call is active or
   * dialing and video reception is enabled.
   *
   * @param videoState The current video state.
   * @param callState The current call state.
   * @return {@code true} if the incoming video surface should be shown, {@code false} otherwise.
   */
  public static boolean showIncomingVideo(int videoState, int callState) {
    if (!CompatUtils.isVideoCompatible()) {
      return false;
    }

    boolean isPaused = VideoProfile.isPaused(videoState);
    boolean isCallActive = callState == DialerCall.State.ACTIVE;
    //Show incoming Video for dialing calls to support early media
    boolean isCallOutgoingPending =
        DialerCall.State.isDialing(callState) || callState == DialerCall.State.CONNECTING;

    return !isPaused
        && (isCallActive || isCallOutgoingPending)
        && VideoProfile.isReceptionEnabled(videoState) && mIsIncomingVideoAvailable;
  }

  /**
   * Determines if the outgoing video surface should be shown based on the current videoState. The
   * video surface is shown if video transmission is enabled.
   *
   * @return {@code true} if the the outgoing video surface should be shown, {@code false}
   *     otherwise.
   */
  public static boolean showOutgoingVideo(
      Context context, int videoState, int sessionModificationState) {
    if (!VideoUtils.hasCameraPermissionAndShownPrivacyToast(context)) {
      LogUtil.i("VideoCallPresenter.showOutgoingVideo", "Camera permission is disabled by user.");
      return false;
    }

    if (!CompatUtils.isVideoCompatible()) {
      return false;
    }

    return VideoProfile.isTransmissionEnabled(videoState)
        || isVideoUpgrade(sessionModificationState);
  }

  public static boolean showOutgoingVideo(
      Context context, int videoState, int sessionModificationState,
      boolean isModifyToVideoRxType) {
    if (!VideoUtils.hasCameraPermissionAndShownPrivacyToast(context)) {
      LogUtil.i("VideoCallPresenter.showOutgoingVideo", "Camera permission is disabled by user.");
      return false;
    }

    if (!CompatUtils.isVideoCompatible()) {
      return false;
    }

    return VideoProfile.isTransmissionEnabled(videoState)
        || (!isModifyToVideoRxType &&
        VideoProfile.isAudioOnly(videoState) &&
        isVideoUpgrade(sessionModificationState));
  }

  private static void updateCameraSelection(DialerCall call) {
    LogUtil.v("VideoCallPresenter.updateCameraSelection", "call=" + call);
    LogUtil.v("VideoCallPresenter.updateCameraSelection", "call=" + toSimpleString(call));

    final DialerCall activeCall = CallList.getInstance().getActiveCall();
    int cameraDir;

    // this function should never be called with null call object, however if it happens we
    // should handle it gracefully.
    if (call == null) {
      cameraDir = CameraDirection.CAMERA_DIRECTION_UNKNOWN;
      LogUtil.e(
          "VideoCallPresenter.updateCameraSelection",
          "call is null. Setting camera direction to default value (CAMERA_DIRECTION_UNKNOWN)");
    }

    // Clear camera direction if this is not a video call.
    else if (isAudioCall(call) && !isVideoUpgrade(call)) {
      cameraDir = CameraDirection.CAMERA_DIRECTION_UNKNOWN;
      call.setCameraDir(cameraDir);
    }

    // If this is a waiting video call, default to active call's camera,
    // since we don't want to change the current camera for waiting call
    // without user's permission.
    else if (isVideoCall(activeCall) && isIncomingVideoCall(call)) {
      cameraDir = activeCall.getCameraDir();
    }

    // Infer the camera direction from the video state and store it,
    // if this is an outgoing video call.
    else if (isOutgoingVideoCall(call) && !isCameraDirectionSet(call)) {
      cameraDir = toCameraDirection(call.getVideoState());
      call.setCameraDir(cameraDir);
    }

    // Use the stored camera dir if this is an outgoing video call for which camera direction
    // is set.
    else if (isOutgoingVideoCall(call)) {
      cameraDir = call.getCameraDir();
    }

    // Infer the camera direction from the video state and store it,
    // if this is an active video call and camera direction is not set.
    else if (isActiveVideoCall(call) && !isCameraDirectionSet(call)) {
      cameraDir = toCameraDirection(call.getVideoState());
      call.setCameraDir(cameraDir);
    }

    // Use the stored camera dir if this is an active video call for which camera direction
    // is set.
    else if (isActiveVideoCall(call)) {
      cameraDir = call.getCameraDir();
    }

    // For all other cases infer the camera direction but don't store it in the call object.
    else {
      cameraDir = toCameraDirection(call.getVideoState());
    }

    LogUtil.i(
        "VideoCallPresenter.updateCameraSelection",
        "setting camera direction to %d, call: %s",
        cameraDir,
        call);
    final InCallCameraManager cameraManager =
        InCallPresenter.getInstance().getInCallCameraManager();
    cameraManager.setUseFrontFacingCamera(
        cameraDir == CameraDirection.CAMERA_DIRECTION_FRONT_FACING);
  }

  private static int toCameraDirection(int videoState) {
    return VideoProfile.isTransmissionEnabled(videoState)
            && !VideoProfile.isBidirectional(videoState)
        ? CameraDirection.CAMERA_DIRECTION_BACK_FACING
        : CameraDirection.CAMERA_DIRECTION_FRONT_FACING;
  }

  private static boolean isCameraDirectionSet(DialerCall call) {
    return isVideoCall(call) && call.getCameraDir() != CameraDirection.CAMERA_DIRECTION_UNKNOWN;
  }

  private static String toSimpleString(DialerCall call) {
    return call == null ? null : call.toSimpleString();
  }

  /**
   * Initializes the presenter.
   *
   * @param context The current context.
   */
  @Override
  public void initVideoCallScreenDelegate(Context context, VideoCallScreen videoCallScreen) {
    mContext = context;
    mPictureModeHelper = new PictureModeHelper(mContext);
    mVideoCallScreen = videoCallScreen;
    mIsAutoFullscreenEnabled =
        mContext.getResources().getBoolean(R.bool.video_call_auto_fullscreen);
    mAutoFullscreenTimeoutMillis =
        mContext.getResources().getInteger(R.integer.video_call_auto_fullscreen_timeout);
    setFixedPreviewSurfaceSize();
  }

  /** Called when the user interface is ready to be used. */
  @Override
  public void onVideoCallScreenUiReady(VideoCallScreen videoCallScreen) {
    LogUtil.v("VideoCallPresenter.onVideoCallScreenUiReady", "");
    Assert.checkState(!isVideoCallScreenUiReady);

    // Do not register any listeners if video calling is not compatible to safeguard against
    // any accidental calls of video calling code.
    if (!CompatUtils.isVideoCompatible()) {
      return;
    }

    mVideoCallScreen = videoCallScreen;
    mDeviceOrientation = InCallOrientationEventListener.getCurrentOrientation();

    // Register for call state changes last
    InCallPresenter.getInstance().addListener(this);
    InCallPresenter.getInstance().addDetailsListener(this);
    InCallPresenter.getInstance().addIncomingCallListener(this);
    InCallPresenter.getInstance().addOrientationListener(this);
    // To get updates of video call details changes
    InCallPresenter.getInstance().addInCallEventListener(this);
    InCallPresenter.getInstance().getLocalVideoSurfaceTexture().setDelegate(new LocalDelegate());
    InCallPresenter.getInstance().getRemoteVideoSurfaceTexture().setDelegate(new RemoteDelegate());

    // Register for surface and video events from {@link InCallVideoCallListener}s.
    InCallVideoCallCallbackNotifier.getInstance().addSurfaceChangeListener(this);
    mPictureModeHelper.setUp(this);

    InCallPresenter.InCallState inCallState = InCallPresenter.getInstance().getInCallState();
    onStateChange(inCallState, inCallState, CallList.getInstance());
    InCallUiStateNotifier.getInstance().addListener(this, true);
    isVideoCallScreenUiReady = true;
    InCallVideoCallCallbackNotifier.getInstance().addVideoEventListener(this,
        VideoProfile.isVideo(mCurrentVideoState));
  }

  /** Called when the user interface is no longer ready to be used. */
  @Override
  public void onVideoCallScreenUiUnready() {
    LogUtil.v("VideoCallPresenter.onVideoCallScreenUiUnready", "");
    Assert.checkState(isVideoCallScreenUiReady);

    if (!CompatUtils.isVideoCompatible()) {
      return;
    }
    onUiShowing(false);
    cancelAutoFullScreen();

    InCallPresenter.getInstance().removeListener(this);
    InCallPresenter.getInstance().removeDetailsListener(this);
    InCallPresenter.getInstance().removeIncomingCallListener(this);
    InCallPresenter.getInstance().removeOrientationListener(this);
    InCallPresenter.getInstance().removeInCallEventListener(this);
    InCallPresenter.getInstance().getLocalVideoSurfaceTexture().setDelegate(null);

    InCallVideoCallCallbackNotifier.getInstance().removeSurfaceChangeListener(this);
    InCallUiStateNotifier.getInstance().removeListener(this);
    InCallVideoCallCallbackNotifier.getInstance().removeVideoEventListener(this);
    mPictureModeHelper.tearDown(this);

    // Ensure that the call's camera direction is updated (most likely to UNKNOWN). Normally this
    // happens after any call state changes but we're unregistering from InCallPresenter above so
    // we won't get any more call state changes. See b/32957114.
    if (mPrimaryCall != null) {
      maybeUnsetPauseImage();
      updateCameraSelection(mPrimaryCall);
    }

    mVideoCallScreen = null;
    isVideoCallScreenUiReady = false;
  }

  public static void cleanUp() {
    LogUtil.v("VideoCallPresenter.cleanUp", "");
   sShallTransmitStaticImage = false;
   sUseDefaultImage = false;
   mIsIncomingVideoAvailable = false;
   mIsVideoMode = false;
  }

  /**
   * Handles clicks on the video surfaces. If not currently in fullscreen mode, will set fullscreen.
   */
  private void onSurfaceClick() {
    LogUtil.i("VideoCallPresenter.onSurfaceClick", "");
    cancelAutoFullScreen();
    if (!InCallPresenter.getInstance().isFullscreen()) {
      InCallPresenter.getInstance().setFullScreen(true);
    } else {
      InCallPresenter.getInstance().setFullScreen(false);
      maybeAutoEnterFullscreen(mPrimaryCall);
      // If Activity is not multiwindow, fullscreen will be driven by SystemUI visibility changes
      // instead. See #onSystemUiVisibilityChange(boolean)

      // TODO (keyboardr): onSystemUiVisibilityChange isn't being called the first time
      // visibility changes after orientation change, so this is currently always done as a backup.
    }
  }

  @Override
  public void onSystemUiVisibilityChange(boolean visible) {
    // If the SystemUI has changed to be visible, take us out of fullscreen mode
    LogUtil.i("VideoCallPresenter.onSystemUiVisibilityChange", "visible: " + visible);
    if (visible) {
      InCallPresenter.getInstance().setFullScreen(false);
      maybeAutoEnterFullscreen(mPrimaryCall);
    }
  }

  @Override
  public VideoSurfaceTexture getLocalVideoSurfaceTexture() {
    return InCallPresenter.getInstance().getLocalVideoSurfaceTexture();
  }

  @Override
  public VideoSurfaceTexture getRemoteVideoSurfaceTexture() {
    return InCallPresenter.getInstance().getRemoteVideoSurfaceTexture();
  }

  @Override
  public void setSurfaceViews(SurfaceView preview, SurfaceView remote) {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public int getDeviceOrientation() {
    return mDeviceOrientation;
  }

  /**
   * This should only be called when user approved the camera permission, which is local action and
   * does NOT change any call states.
   */
  @Override
  public void onCameraPermissionGranted() {
    LogUtil.i("VideoCallPresenter.onCameraPermissionGranted", "");
    if (mPrimaryCall == null) {
      LogUtil.w("VideoCallPresenter.onCameraPermissionGranted",
          "Primary call is null. Not enabling camera");
      return;
    }
    PermissionsUtil.setCameraPrivacyToastShown(mContext);
    enableCamera(mPrimaryCall.getVideoCall(), isCameraRequired());
    showVideoUi(
        mPrimaryCall.getVideoState(),
        mPrimaryCall.getState(),
        mPrimaryCall.getVideoTech().getSessionModificationState(),
        mPrimaryCall.isRemotelyHeld());
    InCallPresenter.getInstance().getInCallCameraManager().onCameraPermissionGranted();
  }

  /**
   * Called when the user interacts with the UI. If a fullscreen timer is pending then we start the
   * timer from scratch to avoid having the UI disappear while the user is interacting with it.
   */
  @Override
  public void resetAutoFullscreenTimer() {
    if (mAutoFullScreenPending) {
      LogUtil.i("VideoCallPresenter.resetAutoFullscreenTimer", "resetting");
      mHandler.removeCallbacks(mAutoFullscreenRunnable);
      mHandler.postDelayed(mAutoFullscreenRunnable, mAutoFullscreenTimeoutMillis);
    }
  }

  /**
   * Handles incoming calls.
   *
   * @param oldState The old in call state.
   * @param newState The new in call state.
   * @param call The call.
   */
  @Override
  public void onIncomingCall(
      InCallPresenter.InCallState oldState, InCallPresenter.InCallState newState, DialerCall call) {
    // same logic should happen as with onStateChange()
    onStateChange(oldState, newState, CallList.getInstance());
  }

  /**
   * Handles state changes (including incoming calls)
   *
   * @param newState The in call state.
   * @param callList The call list.
   */
  @Override
  public void onStateChange(
      InCallPresenter.InCallState oldState,
      InCallPresenter.InCallState newState,
      CallList callList) {
    LogUtil.v(
        "VideoCallPresenter.onStateChange",
        "oldState: %s, newState: %s, isVideoMode: %b",
        oldState,
        newState,
        isVideoMode());

    if (newState == InCallPresenter.InCallState.NO_CALLS) {
      if (isVideoMode()) {
        exitVideoMode();
      }
      sShallTransmitStaticImage = false;
      sUseDefaultImage = false;
      InCallPresenter.getInstance().cleanupSurfaces();
    }

    // Determine the primary active call).
    DialerCall primary = null;

    // Determine the call which is the focus of the user's attention.  In the case of an
    // incoming call waiting call, the primary call is still the active video call, however
    // the determination of whether we should be in fullscreen mode is based on the type of the
    // incoming call, not the active video call.
    DialerCall currentCall = null;

    if (newState == InCallPresenter.InCallState.INCOMING) {
      // We don't want to replace active video call (primary call)
      // with a waiting call, since user may choose to ignore/decline the waiting call and
      // this should have no impact on current active video call, that is, we should not
      // change the camera or UI unless the waiting VT call becomes active.
      primary = callList.getActiveCall();
      currentCall = callList.getIncomingCall();
      if (!isActiveVideoCall(primary)) {
        primary = callList.getIncomingCall();
      }
    } else if (newState == InCallPresenter.InCallState.OUTGOING) {
      currentCall = primary = callList.getOutgoingCall();
    } else if (newState == InCallPresenter.InCallState.PENDING_OUTGOING) {
      currentCall = primary = callList.getPendingOutgoingCall();
    } else if (newState == InCallPresenter.InCallState.INCALL) {
      currentCall = primary = callList.getActiveCall();
    }

    final boolean primaryChanged = !Objects.equals(mPrimaryCall, primary);
    LogUtil.i(
        "VideoCallPresenter.onStateChange",
        "primaryChanged: %b, primary: %s, mPrimaryCall: %s",
        primaryChanged,
        primary,
        mPrimaryCall);
    if (primaryChanged) {
      onPrimaryCallChanged(primary);
    } else if (mPrimaryCall != null) {
      updateVideoCall(primary);
    }
    updateCallCache(primary);

    // If the call context changed, potentially exit fullscreen or schedule auto enter of
    // fullscreen mode.
    // If the current call context is no longer a video call, exit fullscreen mode.
    maybeExitFullscreen(currentCall);
    // Schedule auto-enter of fullscreen mode if the current call context is a video call
    maybeAutoEnterFullscreen(currentCall);
  }

  /**
   * Handles a change to the fullscreen mode of the app.
   *
   * @param isFullscreenMode {@code true} if the app is now fullscreen, {@code false} otherwise.
   */
  @Override
  public void onFullscreenModeChanged(boolean isFullscreenMode) {
    cancelAutoFullScreen();
    if (mPrimaryCall != null) {
      updateFullscreenAndGreenScreenMode(
          mPrimaryCall.getState(), mPrimaryCall.getVideoTech().getSessionModificationState());
    } else {
      updateFullscreenAndGreenScreenMode(State.INVALID, SessionModificationState.NO_REQUEST);
    }
  }

  @Override
  public void onSessionModificationStateChange(DialerCall call) {
    //No-op
  }

  /**
   * Handles a change to the video call hide me selection
   *
   * @param shallTransmitStaticImage {@code true} if the app should show static image in preview,
   * {@code false} otherwise.
   */
   @Override
   public void onSendStaticImageStateChanged(boolean shallTransmitStaticImage) {
    LogUtil.d("VideoCallPresenter.onSendStaticImageStateChanged"," shallTransmitStaticImage: "
        + shallTransmitStaticImage + " mPrimaryCall: " + mPrimaryCall);

    sShallTransmitStaticImage = shallTransmitStaticImage;

    if (!isActiveVideoCall(mPrimaryCall)) {
      LogUtil.w("VideoCallPresenter.onSendStaticImageStateChanged",
          " received for non-active video call");
      return;
    }

    if (mVideoCall == null || mVideoCallScreen == null) {
      LogUtil.w("VideoCallPresenter.onSendStaticImageStateChanged",
          " VideoCall/mVideoCallScreen is null");
      return;
    }

    enableCamera(mVideoCall, isCameraRequired(mCurrentVideoState,
        SessionModificationState.NO_REQUEST));

    if (shallTransmitStaticImage) {
      // Handle showing static image in preview based on external storage permissions
      mVideoCallScreen.onRequestReadStoragePermission();
    } else {
      /* When not required to transmit static image, update video ui visibility
         to reflect streaming video in preview */
      showVideoUi(
          mCurrentVideoState,
          mCurrentCallState,
          SessionModificationState.NO_REQUEST,
          false /* isRemotelyHeld */);
      mVideoCall.setPauseImage(null);
    }
  }

  @Override
  public void onReadStoragePermissionResponse(boolean isGranted) {
    LogUtil.d("VideoCallPresenter.onReadStoragePermissionResponse"," granted = " + isGranted);

    // Use default image when permissions are not granted
    sUseDefaultImage = !isGranted;
    if (!isGranted) {
      QtiCallUtils.displayToast(mContext, R.string.qti_ims_defaultImage_fallback);
    }

    showVideoUi(
        mCurrentVideoState,
        mCurrentCallState,
        SessionModificationState.NO_REQUEST,
        false /* isRemotelyHeld */);
  }

  private void checkForVideoStateChange(DialerCall call) {
    final boolean shouldShowVideoUi = shouldShowVideoUiForCall(call);
    final boolean hasVideoStateChanged = mCurrentVideoState != call.getVideoState();

    LogUtil.v(
        "VideoCallPresenter.checkForVideoStateChange",
        "shouldShowVideoUi: %b, hasVideoStateChanged: %b, isVideoMode: %b, previousVideoState: %s,"
            + " newVideoState: %s",
        shouldShowVideoUi,
        hasVideoStateChanged,
        isVideoMode(),
        VideoProfile.videoStateToString(mCurrentVideoState),
        VideoProfile.videoStateToString(call.getVideoState()));
    if (!hasVideoStateChanged) {
      return;
    }

    // Wakes up the screen,if its off, when user upgrades to VT call.
    if (VideoProfile.isAudioOnly(mCurrentVideoState) && isVideoCall(call)) {
      InCallPresenter.getInstance().wakeUpScreen();
    }

    maybeUnsetPauseImage();
    updateCameraSelection(call);

    if (shouldShowVideoUi) {
      adjustVideoMode(call);
    } else if (isVideoMode()) {
      exitVideoMode();
    }
  }

  private void maybeUnsetPauseImage() {
    if (QtiImsExtUtils.shallTransmitStaticImage(
            BottomSheetHelper.getInstance().getPhoneId(), mContext) &&
        shallTransmitStaticImage() &&
        !isTransmissionEnabled(mPrimaryCall) &&
        mVideoCall != null) {
      /* Unset the pause image when Tx is disabled for eg. when video call
         that is transmitting static image is downgraded to Rx or to voice */
      mVideoCall.setPauseImage(null);
    }
  }

  private void checkForCallStateChange(DialerCall call) {
    final boolean shouldShowVideoUi = shouldShowVideoUiForCall(call);
    final boolean hasCallStateChanged =
        mCurrentCallState != call.getState() || mIsRemotelyHeld != call.isRemotelyHeld();
    mIsRemotelyHeld = call.isRemotelyHeld();

    LogUtil.v(
        "VideoCallPresenter.checkForCallStateChange",
        "shouldShowVideoUi: %b, hasCallStateChanged: %b, isVideoMode: %b",
        shouldShowVideoUi,
        hasCallStateChanged,
        isVideoMode());

    if (!hasCallStateChanged) {
      return;
    }

    if (shouldShowVideoUi) {
      final InCallCameraManager cameraManager =
          InCallPresenter.getInstance().getInCallCameraManager();

      String prevCameraId = cameraManager.getActiveCameraId();
      updateCameraSelection(call);
      String newCameraId = cameraManager.getActiveCameraId();

      if (!Objects.equals(prevCameraId, newCameraId) && isActiveVideoCall(call)) {
        enableCamera(call.getVideoCall(), true);
      }
    }

    // Make sure we hide or show the video UI if needed.
    showVideoUi(
        call.getVideoState(),
        call.getState(),
        call.getVideoTech().getSessionModificationState(),
        call.isRemotelyHeld());
  }

  private void onPrimaryCallChanged(DialerCall newPrimaryCall) {
    final boolean shouldShowVideoUi = shouldShowVideoUiForCall(newPrimaryCall);
    final boolean isVideoMode = isVideoMode();

    LogUtil.v(
        "VideoCallPresenter.onPrimaryCallChanged",
        "shouldShowVideoUi: %b, isVideoMode: %b",
        shouldShowVideoUi,
        isVideoMode);

    if (!shouldShowVideoUi && isVideoMode) {
      // Terminate video mode if new primary call is not a video call
      // and we are currently in video mode.
      LogUtil.i("VideoCallPresenter.onPrimaryCallChanged", "exiting video mode...");
      exitVideoMode();
    } else if (shouldShowVideoUi) {
      LogUtil.i("VideoCallPresenter.onPrimaryCallChanged", "entering video mode...");

      checkForOrientationAllowedChange(newPrimaryCall);
      updateCameraSelection(newPrimaryCall);

      // Existing call is put on hold and new call is in incoming state does mean that
      // user is trying to answer the call
      if (isIncomingVideoCall(newPrimaryCall) &&
          isTransmissionEnabled(mPrimaryCall) &&
          mPrimaryCall.getState() == DialerCall.State.ONHOLD) {
        // Close camera on mPrimaryCall
        LogUtil.v("VideoCallPresenter.onPrimaryCallChanged", "closing camera");
        enableCamera(mPrimaryCall.getVideoCall(), false);
      }
      adjustVideoMode(newPrimaryCall);
    }
  }

  private boolean isVideoMode() {
    return mIsVideoMode;
  }

  private void updateCallCache(DialerCall call) {
    if (call == null) {
      mCurrentVideoState = VideoProfile.STATE_AUDIO_ONLY;
      mCurrentCallState = DialerCall.State.INVALID;
      mVideoCall = null;
      mPrimaryCall = null;
    } else {
      mCurrentVideoState = call.getVideoState();
      mVideoCall = call.getVideoCall();
      mCurrentCallState = call.getState();
      mPrimaryCall = call;
    }
  }

  /**
   * Handles changes to the details of the call. The {@link VideoCallPresenter} is interested in
   * changes to the video state.
   *
   * @param call The call for which the details changed.
   * @param details The new call details.
   */
  @Override
  public void onDetailsChanged(DialerCall call, android.telecom.Call.Details details) {
    LogUtil.v(
        "VideoCallPresenter.onDetailsChanged",
        "call: %s, details: %s, mPrimaryCall: %s",
        call,
        details,
        mPrimaryCall);
    if (call == null) {
      return;
    }
    // If the details change is not for the currently active call no update is required.
    if (!call.equals(mPrimaryCall)) {
      LogUtil.v("VideoCallPresenter.onDetailsChanged", "details not for current active call");
      return;
    }

    updateVideoCall(call);

    updateCallCache(call);
  }

  private void updateVideoCall(DialerCall call) {
    checkForVideoCallChange(call);
    checkForVideoStateChange(call);
    checkForCallStateChange(call);
    checkForOrientationAllowedChange(call);
    updateFullscreenAndGreenScreenMode(
        call.getState(), call.getVideoTech().getSessionModificationState());
  }

  private void checkForOrientationAllowedChange(@Nullable DialerCall call) {
    int orientation = OrientationModeHandler.getInstance().getOrientation(call);
    LogUtil.d("VideoCallPresenter.checkForOrientationAllowedChange","call : "+ call +
        " mCurrentOrientationMode : " + mCurrentOrientationMode + " orientation : " + orientation);
    if (orientation != mCurrentOrientationMode &&
        InCallPresenter.getInstance().setInCallAllowsOrientationChange(orientation)) {
      mCurrentOrientationMode = orientation;
    }
  }

  private void updateFullscreenAndGreenScreenMode(
      int callState, @SessionModificationState int sessionModificationState) {
    if (mVideoCallScreen != null) {
      boolean hasVideoCallSentVideoUpgradeRequest =
          isVideoCall(mPrimaryCall)
          && VideoUtils.hasSentVideoUpgradeRequest(sessionModificationState);

      boolean shouldShowFullscreen = InCallPresenter.getInstance().isFullscreen();

      /*
       * Do not enter green screen mode:
       * 1. For VoLTE to VT-RX upgrade
       * 2. If a video call is waiting for upgrade to video response
       *    for eg. VT->VT-RX/VT-TX or VT-TX/VT-RX->VT etc.,
       * 3. If incoming video is available for dialing call to support
       *    early media
       */
      boolean shouldShowGreenScreen =
          ((callState == State.DIALING
              || callState == State.CONNECTING) && !mIsIncomingVideoAvailable)
              || callState == State.INCOMING
              || (!hasVideoCallSentVideoUpgradeRequest
              && !isModifyToVideoRxType(mPrimaryCall)
              && isVideoUpgrade(sessionModificationState));
      mVideoCallScreen.updateFullscreenAndGreenScreenMode(
          shouldShowFullscreen, shouldShowGreenScreen);
    }
  }

  /** Checks for a change to the video call and changes it if required. */
  private void checkForVideoCallChange(DialerCall call) {
    final VideoCall videoCall = call.getVideoCall();
    LogUtil.v(
        "VideoCallPresenter.checkForVideoCallChange",
        "videoCall: %s, mVideoCall: %s",
        videoCall,
        mVideoCall);
    if (!Objects.equals(videoCall, mVideoCall)) {
      changeVideoCall(call);
    }
  }

  /**
   * Handles a change to the video call. Sets the surfaces on the previous call to null and sets the
   * surfaces on the new video call accordingly.
   *
   * @param call The new video call.
   */
  private void changeVideoCall(DialerCall call) {
    final VideoCall videoCall = call == null ? null : call.getVideoCall();
    LogUtil.i(
        "VideoCallPresenter.changeVideoCall",
        "videoCall: %s, mVideoCall: %s",
        videoCall,
        mVideoCall);
    final boolean hasChanged = mVideoCall == null && videoCall != null;

    mVideoCall = videoCall;
    if (mVideoCall == null) {
      LogUtil.v("VideoCallPresenter.changeVideoCall", "video call or primary call is null. Return");
      return;
    }

    if (shouldShowVideoUiForCall(call) && hasChanged) {
      adjustVideoMode(call);
    }
  }

  private boolean isCameraRequired() {
    return mPrimaryCall != null
        && isCameraRequired(
            mPrimaryCall.getVideoState(),
            mPrimaryCall.getVideoTech().getSessionModificationState());
  }

  /**
   * Adjusts the current video mode by setting up the preview and display surfaces as necessary.
   * Expected to be called whenever the video state associated with a call changes (e.g. a user
   * turns their camera on or off) to ensure the correct surfaces are shown/hidden. TODO: Need
   * to adjust size and orientation of preview surface here.
   */
  private void adjustVideoMode(DialerCall call) {
    VideoCall videoCall = call.getVideoCall();
    int newVideoState = call.getVideoState();

    LogUtil.i(
        "VideoCallPresenter.adjustVideoMode",
        "videoCall: %s, videoState: %d",
        videoCall,
        newVideoState);
    if (mVideoCallScreen == null) {
      LogUtil.e("VideoCallPresenter.adjustVideoMode", "error VideoCallScreen is null so returning");
      return;
    }

    showVideoUi(
        newVideoState,
        call.getState(),
        call.getVideoTech().getSessionModificationState(),
        call.isRemotelyHeld());

    // Communicate the current camera to telephony and make a request for the camera
    // capabilities.
    if (videoCall != null) {
      Surface surface = getRemoteVideoSurfaceTexture().getSavedSurface();
      if (surface != null) {
        LogUtil.v(
            "VideoCallPresenter.adjustVideoMode", "calling setDisplaySurface with: " + surface);
        videoCall.setDisplaySurface(surface);
      }

      Assert.checkState(
          mDeviceOrientation != InCallOrientationEventListener.SCREEN_ORIENTATION_UNKNOWN);
      videoCall.setDeviceOrientation(mDeviceOrientation);
      enableCamera(
          videoCall,
          isCameraRequired(newVideoState, call.getVideoTech().getSessionModificationState()));

      if (QtiImsExtUtils.shallShowStaticImageUi(BottomSheetHelper.getInstance().getPhoneId(),
          mContext) && shallTransmitStaticImage()) {
        /* when call downgrades and later upgrades, mVideoCall can be null that prevents setting
           pause image to lower layers so invoke setPauseImage with videocall obj as parameter */
        setPauseImage(videoCall);
      }
    }
    int previousVideoState = mCurrentVideoState;
    mCurrentVideoState = newVideoState;
    mIsVideoMode = true;

    // adjustVideoMode may be called if we are already in a 1-way video state.  In this case
    // we do not want to trigger auto-fullscreen mode.
    if (!isVideoCall(previousVideoState) && isVideoCall(newVideoState)) {
      maybeAutoEnterFullscreen(call);
    }
  }

  private static boolean shouldShowVideoUiForCall(@Nullable DialerCall call) {
    if (call == null) {
      return false;
    }

    if (isVideoCall(call)) {
      return true;
    }

    if (isVideoUpgrade(call)) {
      return true;
    }

    return false;
  }

  private void enableCamera(VideoCall videoCall, boolean isCameraRequired) {
    LogUtil.v(
        "VideoCallPresenter.enableCamera",
        "videoCall: %s, enabling: %b",
        videoCall,
        isCameraRequired);
    if (videoCall == null) {
      LogUtil.i("VideoCallPresenter.enableCamera", "videoCall is null.");
      return;
    }

    boolean hasCameraPermission = VideoUtils.hasCameraPermissionAndShownPrivacyToast(mContext);
    if (!hasCameraPermission) {
      videoCall.setCamera(null);
      mPreviewSurfaceState = PreviewSurfaceState.NONE;
      // TODO: Inform remote party that the video is off. This is similar to b/30256571.
    } else if (isCameraRequired) {
      InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
      videoCall.setCamera(cameraManager.getActiveCameraId());
      InCallZoomController.getInstance().onCameraEnabled(cameraManager.getActiveCameraId());
      mPreviewSurfaceState = PreviewSurfaceState.CAMERA_SET;
      videoCall.requestCameraCapabilities();
    } else {
      mPreviewSurfaceState = PreviewSurfaceState.NONE;
      videoCall.setCamera(null);
      InCallZoomController.getInstance().onCameraEnabled(null);
    }
  }

  /** Exits video mode by hiding the video surfaces and making other adjustments (eg. audio). */
  private void exitVideoMode() {
    LogUtil.i("VideoCallPresenter.exitVideoMode", "");

    showVideoUi(
        VideoProfile.STATE_AUDIO_ONLY,
        DialerCall.State.ACTIVE,
        SessionModificationState.NO_REQUEST,
        false /* isRemotelyHeld */);
    enableCamera(mVideoCall, false);
    InCallPresenter.getInstance().setFullScreen(false);
    InCallPresenter.getInstance().enableScreenTimeout(true);
    checkForOrientationAllowedChange(mPrimaryCall);

    if (mPrimaryCall != null &&
        mVideoCall != null &&
        QtiImsExtUtils.shallTransmitStaticImage(
            BottomSheetHelper.getInstance().getPhoneId(), mContext) &&
        isTransmissionEnabled(mPrimaryCall) &&
        mPrimaryCall.getState() != DialerCall.State.ONHOLD) {
      LogUtil.v("VideoCallPresenter.exitVideoMode", "setPauseImage(null)");
      mVideoCall.setPauseImage(null);
    }

    mIsVideoMode = false;
  }

  /**
   * Based on the current video state and call state, show or hide the incoming and outgoing video
   * surfaces. The outgoing video surface is shown any time video is transmitting. The incoming
   * video surface is shown whenever the video is un-paused and active.
   *
   * @param videoState The video state.
   * @param callState The call state.
   */
  private void showVideoUi(
      int videoState,
      int callState,
      @SessionModificationState int sessionModificationState,
      boolean isRemotelyHeld) {
    if (mVideoCallScreen == null) {
      LogUtil.e("VideoCallPresenter.showVideoUi", "videoCallScreen is null returning");
      return;
    }
    boolean isModifyToVideoRxType = isModifyToVideoRxType(mPrimaryCall);
    boolean showIncomingVideo = showIncomingVideo(videoState, callState);
    boolean showOutgoingVideo = showOutgoingVideo(mContext, videoState, sessionModificationState,
        isModifyToVideoRxType);
    LogUtil.i(
        "VideoCallPresenter.showVideoUi",
        "showIncoming: %b, showOutgoing: %b, isRemotelyHeld: %b shallTransmitStaticImage: %b" +
         " isModifyToVideoRx: %b",
        showIncomingVideo,
        showOutgoingVideo,
        isRemotelyHeld,
        shallTransmitStaticImage(),
        isModifyToVideoRxType);
    updateRemoteVideoSurfaceDimensions();
    mVideoCallScreen.showVideoViews(showOutgoingVideo && !shallTransmitStaticImage() &&
        !QtiCallUtils.hasVideoCrbtVoLteCall(), showIncomingVideo, isRemotelyHeld);
    if (BottomSheetHelper.getInstance().canDisablePipMode() && mPictureModeHelper != null) {
      mPictureModeHelper.setPreviewVideoLayoutParams();
    }

    updateFullscreenAndGreenScreenMode(callState, sessionModificationState);
    InCallPresenter.getInstance().enableScreenTimeout(VideoProfile.isAudioOnly(videoState));
    if (BottomSheetHelper.getInstance().canDisablePipMode() && mPictureModeHelper != null) {
      mPictureModeHelper.maybeHideVideoViews();
    }
  }

  /**
   * Handles peer video dimension changes.
   *
   * @param call The call which experienced a peer video dimension change.
   * @param width The new peer video width .
   * @param height The new peer video height.
   */
  @Override
  public void onUpdatePeerDimensions(DialerCall call, int width, int height) {
    LogUtil.i("VideoCallPresenter.onUpdatePeerDimensions", "width: %d, height: %d", width, height);
    if (mVideoCallScreen == null) {
      LogUtil.e("VideoCallPresenter.onUpdatePeerDimensions", "videoCallScreen is null");
      return;
    }
    if (!call.equals(mPrimaryCall)) {
      LogUtil.e(
          "VideoCallPresenter.onUpdatePeerDimensions", "current call is not equal to primary");
      return;
    }

    // Change size of display surface to match the peer aspect ratio
    if (width > 0 && height > 0 && mVideoCallScreen != null) {
      getRemoteVideoSurfaceTexture().setSourceVideoDimensions(new Point(width, height));
      mVideoCallScreen.onRemoteVideoDimensionsChanged();
    }
  }

  /**
   * Handles a change to the dimensions of the local camera. Receiving the camera capabilities
   * triggers the creation of the video
   *
   * @param call The call which experienced the camera dimension change.
   * @param width The new camera video width.
   * @param height The new camera video height.
   */
  @Override
  public void onCameraDimensionsChange(DialerCall call, int width, int height) {
    LogUtil.i(
        "VideoCallPresenter.onCameraDimensionsChange",
        "call: %s, width: %d, height: %d",
        call,
        width,
        height);
    if (mVideoCallScreen == null) {
      LogUtil.e("VideoCallPresenter.onCameraDimensionsChange", "ui is null");
      return;
    }

    if (!call.equals(mPrimaryCall)) {
      LogUtil.e("VideoCallPresenter.onCameraDimensionsChange", "not the primary call");
      return;
    }

    if (shallTransmitStaticImage()) {
      setPauseImage(call.getVideoCall());
    }

    if (mPreviewSurfaceState == PreviewSurfaceState.NONE) {
      LogUtil.w("VideoCallPresenter.onCameraDimensionsChange",
          "capabilities received when camera is OFF.");
      return;
    }

    mPreviewSurfaceState = PreviewSurfaceState.CAPABILITIES_RECEIVED;

    changePreviewDimensions(width, height);

    // Check if the preview surface is ready yet; if it is, set it on the {@code VideoCall}.
    // If it not yet ready, it will be set when when creation completes.
    Surface surface = getLocalVideoSurfaceTexture().getSavedSurface();
    if (surface != null) {
      mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
      mVideoCall.setPreviewSurface(surface);
    }
  }

  /**
   * Changes the dimensions of the preview surface.
   *
   * @param width The new width.
   * @param height The new height.
   */
  private void changePreviewDimensions(int width, int height) {
    if (mVideoCallScreen == null) {
      return;
    }

    Point previewSize = (mFixedPreviewSurfaceSize != null) ? mFixedPreviewSurfaceSize :
        new Point(width, height);
    LogUtil.i("VideoCallPresenter.changePreviewDimensions", "width: %d, height: %d", previewSize.x,
        previewSize.y);
    // Resize the surface used to display the preview video
    getLocalVideoSurfaceTexture().setSurfaceDimensions(previewSize);
    mVideoCallScreen.onLocalVideoDimensionsChanged();
  }

  /**
   * Handles changes to the device orientation.
   *
   * @param orientation The screen orientation of the device (one of: {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_0}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_90}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_180}, {@link
   *     InCallOrientationEventListener#SCREEN_ORIENTATION_270}).
   */
  @Override
  public void onDeviceOrientationChanged(int orientation) {
    LogUtil.i(
        "VideoCallPresenter.onDeviceOrientationChanged",
        "orientation: %d -> %d",
        mDeviceOrientation,
        orientation);
    mDeviceOrientation = orientation;

    if (mVideoCallScreen == null) {
      LogUtil.e("VideoCallPresenter.onDeviceOrientationChanged", "videoCallScreen is null");
      return;
    }

    Point previewDimensions = getLocalVideoSurfaceTexture().getSurfaceDimensions();
    if (previewDimensions == null) {
      return;
    }
    LogUtil.v(
        "VideoCallPresenter.onDeviceOrientationChanged",
        "orientation: %d, size: %s",
        orientation,
        previewDimensions);
    changePreviewDimensions(previewDimensions.x, previewDimensions.y);

    mVideoCallScreen.onLocalVideoOrientationChanged();
  }

  /**
   * Exits fullscreen mode if the current call context has changed to a non-video call.
   *
   * @param call The call.
   */
  protected void maybeExitFullscreen(DialerCall call) {
    if (call == null) {
      return;
    }

    if (!isVideoCall(call) || call.getState() == DialerCall.State.INCOMING) {
      LogUtil.i("VideoCallPresenter.maybeExitFullscreen", "exiting fullscreen");
      InCallPresenter.getInstance().setFullScreen(false);
    }
  }

  /**
   * Schedules auto-entering of fullscreen mode. Will not enter full screen mode if any of the
   * following conditions are met: 1. No call 2. DialerCall is not active 3. The current video state
   * is not bi-directional. 4. Already in fullscreen mode 5. In accessibility mode
   *
   * @param call The current call.
   */
  protected void maybeAutoEnterFullscreen(DialerCall call) {
    if (!mIsAutoFullscreenEnabled) {
      return;
    }

    if (call == null
        || call.getState() != DialerCall.State.ACTIVE
        || !isBidirectionalVideoCall(call)
        || InCallPresenter.getInstance().isFullscreen()
        || (mContext != null && AccessibilityUtil.isTouchExplorationEnabled(mContext))) {
      // Ensure any previously scheduled attempt to enter fullscreen is cancelled.
      cancelAutoFullScreen();
      return;
    }

    if (mAutoFullScreenPending) {
      LogUtil.v("VideoCallPresenter.maybeAutoEnterFullscreen", "already pending.");
      return;
    }
    LogUtil.v("VideoCallPresenter.maybeAutoEnterFullscreen", "scheduled");
    mAutoFullScreenPending = true;
    mHandler.removeCallbacks(mAutoFullscreenRunnable);
    mHandler.postDelayed(mAutoFullscreenRunnable, mAutoFullscreenTimeoutMillis);
  }

  /** Cancels pending auto fullscreen mode. */
  @Override
  public void cancelAutoFullScreen() {
    if (!mAutoFullScreenPending) {
      LogUtil.v("VideoCallPresenter.cancelAutoFullScreen", "none pending.");
      return;
    }
    LogUtil.v("VideoCallPresenter.cancelAutoFullScreen", "cancelling pending");
    mAutoFullScreenPending = false;
    mHandler.removeCallbacks(mAutoFullscreenRunnable);
  }

  @Override
  public boolean shouldShowCameraPermissionToast() {
    if (mPrimaryCall == null) {
      LogUtil.i("VideoCallPresenter.shouldShowCameraPermissionToast", "null call");
      return false;
    }
    if (mPrimaryCall.didShowCameraPermission()) {
      LogUtil.i(
          "VideoCallPresenter.shouldShowCameraPermissionToast", "already shown for this call");
      return false;
    }
    if (!ConfigProviderBindings.get(mContext)
        .getBoolean("camera_permission_dialog_allowed", true)) {
      LogUtil.i("VideoCallPresenter.shouldShowCameraPermissionToast", "disabled by config");
      return false;
    }
    return !VideoUtils.hasCameraPermission(mContext)
        || !PermissionsUtil.hasCameraPrivacyToastShown(mContext);
  }

  @Override
  public void onCameraPermissionDialogShown() {
    if (mPrimaryCall != null) {
      mPrimaryCall.setDidShowCameraPermission(true);
    }
  }

  private void updateRemoteVideoSurfaceDimensions() {
    if (mVideoCallScreen == null) {
      LogUtil.i("VideoCallPresenter.updateRemoteVideoSurfaceDimensions",
          "mVideoCallScreen is null");
      return;
    }
    Activity activity = mVideoCallScreen.getVideoCallScreenFragment().getActivity();
    if (activity != null) {
      Point screenSize = new Point();
      activity.getWindowManager().getDefaultDisplay().getSize(screenSize);
      getRemoteVideoSurfaceTexture().setSurfaceDimensions(screenSize);
    }
  }

  private static boolean isVideoUpgrade(DialerCall call) {
    return call != null
        && (call.hasSentVideoUpgradeRequest() || call.hasReceivedVideoUpgradeRequest());
  }

  private static boolean isVideoUpgrade(@SessionModificationState int state) {
    return VideoUtils.hasSentVideoUpgradeRequest(state)
        || VideoUtils.hasReceivedVideoUpgradeRequest(state);
  }

  private class LocalDelegate implements VideoSurfaceDelegate {
    @Override
    public void onSurfaceCreated(VideoSurfaceTexture videoCallSurface) {
      if (mVideoCallScreen == null) {
        LogUtil.e("VideoCallPresenter.LocalDelegate.onSurfaceCreated", "no UI");
        return;
      }
      if (mVideoCall == null) {
        LogUtil.e("VideoCallPresenter.LocalDelegate.onSurfaceCreated", "no video call");
        return;
      }

      // If the preview surface has just been created and we have already received camera
      // capabilities, but not yet set the surface, we will set the surface now.
      if (mPreviewSurfaceState == PreviewSurfaceState.CAPABILITIES_RECEIVED) {
        mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
        mVideoCall.setPreviewSurface(videoCallSurface.getSavedSurface());
      } else {
        maybeEnableCamera();
      }
    }

    @Override
    public void onSurfaceReleased(VideoSurfaceTexture videoCallSurface) {
      if (mVideoCall == null) {
        LogUtil.e("VideoCallPresenter.LocalDelegate.onSurfaceReleased", "no video call");
        return;
      }

      mVideoCall.setPreviewSurface(null);
      enableCamera(mVideoCall, false);
    }

    @Override
    public void onSurfaceDestroyed(VideoSurfaceTexture videoCallSurface) {
      if (mVideoCall == null) {
        LogUtil.e("VideoCallPresenter.LocalDelegate.onSurfaceDestroyed", "no video call");
        return;
      }

      boolean isChangingConfigurations = InCallPresenter.getInstance().isChangingConfigurations();
      if (!isChangingConfigurations) {
        enableCamera(mVideoCall, false);
      } else {
        LogUtil.i(
            "VideoCallPresenter.LocalDelegate.onSurfaceDestroyed",
            "activity is being destroyed due to configuration changes. Not closing the camera.");
      }
    }

    @Override
    public void onSurfaceClick(VideoSurfaceTexture videoCallSurface) {
      // Show zoom control when preview surface is clicked.
      LogUtil.i("VideoCallPresenter.onSurfaceClick", "");
      if (shallTransmitStaticImage()) {
        VideoCallPresenter.this.onSurfaceClick();
      } else if (mPictureModeHelper != null && mPictureModeHelper.canShowPreviewVideoView()
          && isActiveVideoCall(mPrimaryCall) && isTransmissionEnabled(mPrimaryCall)) {
        // Set fullscreen to true when showing the zoom controls as the
        // buttons on the left panel conflict with the zoom control bar.
        cancelAutoFullScreen();
        if (!InCallPresenter.getInstance().isFullscreen()) {
          InCallPresenter.getInstance().setFullScreen(true);
        }
        InCallZoomController.getInstance().onPreviewSurfaceClicked(mVideoCall);
      }
    }
  }

  private class RemoteDelegate implements VideoSurfaceDelegate {
    @Override
    public void onSurfaceCreated(VideoSurfaceTexture videoCallSurface) {
      if (mVideoCallScreen == null) {
        LogUtil.e("VideoCallPresenter.RemoteDelegate.onSurfaceCreated", "no UI");
        return;
      }
      if (mVideoCall == null) {
        LogUtil.e("VideoCallPresenter.RemoteDelegate.onSurfaceCreated", "no video call");
        return;
      }
      mVideoCall.setDisplaySurface(videoCallSurface.getSavedSurface());
    }

    @Override
    public void onSurfaceReleased(VideoSurfaceTexture videoCallSurface) {
      if (mVideoCall == null) {
        LogUtil.e("VideoCallPresenter.RemoteDelegate.onSurfaceReleased", "no video call");
        return;
      }
      mVideoCall.setDisplaySurface(null);
    }

    @Override
    public void onSurfaceDestroyed(VideoSurfaceTexture videoCallSurface) {}

    @Override
    public void onSurfaceClick(VideoSurfaceTexture videoCallSurface) {
      VideoCallPresenter.this.onSurfaceClick();
    }
  }

  /** Defines the state of the preview surface negotiation with the telephony layer. */
  private static class PreviewSurfaceState {

    /**
     * The camera has not yet been set on the {@link VideoCall}; negotiation has not yet started.
     */
    private static final int NONE = 0;

    /**
     * The camera has been set on the {@link VideoCall}, but camera capabilities have not yet been
     * received.
     */
    private static final int CAMERA_SET = 1;

    /**
     * The camera capabilties have been received from telephony, but the surface has not yet been
     * set on the {@link VideoCall}.
     */
    private static final int CAPABILITIES_RECEIVED = 2;

    /** The surface has been set on the {@link VideoCall}. */
    private static final int SURFACE_SET = 3;
  }

  private static boolean isBidirectionalVideoCall(DialerCall call) {
    return CompatUtils.isVideoCompatible() && VideoProfile.isBidirectional(call.getVideoState());
  }

  public static boolean isTransmissionEnabled(DialerCall call) {
    return CompatUtils.isVideoCompatible() && call != null &&
        VideoProfile.isTransmissionEnabled(call.getVideoState());
  }

  private static boolean isIncomingVideoCall(DialerCall call) {
    if (!isVideoCall(call)) {
      return false;
    }
    final int state = call.getState();
    return (state == DialerCall.State.INCOMING) || (state == DialerCall.State.CALL_WAITING);
  }

  private static boolean isActiveVideoCall(DialerCall call) {
    return isVideoCall(call) && call.getState() == DialerCall.State.ACTIVE;
  }

  private static boolean isOutgoingVideoCall(DialerCall call) {
    if (!isVideoCall(call)) {
      return false;
    }
    final int state = call.getState();
    return DialerCall.State.isDialing(state)
        || state == DialerCall.State.CONNECTING
        || state == DialerCall.State.SELECT_PHONE_ACCOUNT;
  }

  private static boolean isAudioCall(DialerCall call) {
    if (!CompatUtils.isVideoCompatible()) {
      return true;
    }

    return call != null && VideoProfile.isAudioOnly(call.getVideoState());
  }

  private static boolean isVideoCall(@Nullable DialerCall call) {
    return call != null && call.isVideoCall();
  }

  private static boolean isVideoCall(int videoState) {
    return CompatUtils.isVideoCompatible()
        && (VideoProfile.isTransmissionEnabled(videoState)
            || VideoProfile.isReceptionEnabled(videoState));
  }

  private static boolean isModifyToVideoRxType(DialerCall call) {
    if (!CompatUtils.isVideoCompatible()) {
      return false;
    }

    return call != null
        && (call.getVideoTech().getUpgradeToVideoState() == VideoProfile.STATE_RX_ENABLED ||
        call.getVideoTech().getRequestedVideoState() == VideoProfile.STATE_RX_ENABLED);
  }

  /**
   * Reads the fixed preview size from global settings and caches it
   */
  private void setFixedPreviewSurfaceSize() {
    if (mPictureModeHelper != null) {
      mFixedPreviewSurfaceSize = mPictureModeHelper.getPreviewSizeFromSetting(mContext);
    }
  }

  /**
   * Gets called when preview video selection changes
   * @param boolean previewVideoSelection - New value for preview video selection
   */
  @Override
  public void onPreviewVideoSelectionChanged() {
    if (mPrimaryCall == null) {
      return;
    }
    setFixedPreviewSurfaceSize();
    if (mFixedPreviewSurfaceSize != null) {
      changePreviewDimensions(mFixedPreviewSurfaceSize.x, mFixedPreviewSurfaceSize.y);
    }
    showVideoUi(
        mPrimaryCall.getVideoState(),
        mPrimaryCall.getState(),
        mPrimaryCall.getVideoTech().getSessionModificationState(),
        mPrimaryCall.isRemotelyHeld());
  }

  /**
   * Gets called when incoming video selection changes
   * @param boolean incomingVideoSelection - New value for incoming video selection
   */
  @Override
  public void onIncomingVideoSelectionChanged() {
    if (mPrimaryCall == null) {
      return;
    }
    showVideoUi(
        mPrimaryCall.getVideoState(),
        mPrimaryCall.getState(),
        mPrimaryCall.getVideoTech().getSessionModificationState(),
        mPrimaryCall.isRemotelyHeld());
  }

  public static PictureModeHelper getPictureModeHelper() {
    return mPictureModeHelper;
  }

  public static void showPipModeMenu() {
    if (mPictureModeHelper != null) {
      mPictureModeHelper.createAndShowDialog();
    }
  }

  /**
   * Called when call session event is raised.
   *
   * @param event The call session event.
   */
  @Override
  public void onCallSessionEvent(int event) {
    StringBuilder sb = new StringBuilder();
    sb.append("call session event = ");

    switch (event) {
      case VideoProvider.SESSION_EVENT_RX_PAUSE:
      case VideoProvider.SESSION_EVENT_RX_RESUME:
        mIsIncomingVideoAvailable =
            event == VideoProvider.SESSION_EVENT_RX_RESUME;
        if (mPrimaryCall == null) {
          return;
        }
        showVideoUi(
          mPrimaryCall.getVideoState(),
          mPrimaryCall.getState(),
          mPrimaryCall.getVideoTech().getSessionModificationState(),
          mPrimaryCall.isRemotelyHeld());
        sb.append(mIsIncomingVideoAvailable ? "rx_resume" : "rx_pause");
        break;
      case VideoProvider.SESSION_EVENT_CAMERA_FAILURE:
        sb.append("camera_failure");
        break;
      case VideoProvider.SESSION_EVENT_CAMERA_READY:
        sb.append("camera_ready");
        break;
      default:
        sb.append("unknown event = ");
        sb.append(event);
        break;
    }
    LogUtil.i("VideoCallPresenter.onCallSessionEvent", sb.toString());
  }
}
