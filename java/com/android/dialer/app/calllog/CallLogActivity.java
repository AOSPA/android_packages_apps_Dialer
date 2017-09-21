/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.app.calllog;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.ClearCallLogDialog.Listener;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.compat.AppCompatConstants;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.logging.UiAction;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.util.TransactionSafeActivity;
import com.android.dialer.util.ViewUtil;

/** Activity for viewing call history. */
public class CallLogActivity extends TransactionSafeActivity
    implements ViewPager.OnPageChangeListener, Listener {

  private static final int TAB_INDEX_ALL = 0;
  private static final int TAB_INDEX_MISSED = 1;
  private static final int TAB_INDEX_COUNT = 2;

  private static final int TAB_INDEX_MSIM = 0;
  private static final int TAB_INDEX_COUNT_MSIM = 1;
  private ViewPager mViewPager;
  private ViewPagerTabs mViewPagerTabs;
  private FragmentPagerAdapter mViewPagerAdapter;
  private CallLogFragment mAllCallsFragment;
  private MSimCallLogFragment mMSimCallsFragment;
  private String[] mTabTitles;
  private boolean mIsResumed;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayShowHomeEnabled(true);
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setElevation(0);
    TelephonyManager telephonyManager = (TelephonyManager)
        getSystemService(Context.TELEPHONY_SERVICE);
    if (TelephonyManagerCompat.getPhoneCount(telephonyManager) > 1) {
      initMSimCallLog();
      return;
    }

    setContentView(R.layout.call_log_activity);
    getWindow().setBackgroundDrawable(null);

    int startingTab = TAB_INDEX_ALL;
    final Intent intent = getIntent();
    if (intent != null) {
      final int callType = intent.getIntExtra(CallLog.Calls.EXTRA_CALL_TYPE_FILTER, -1);
      if (callType == CallLog.Calls.MISSED_TYPE ||
          callType == AppCompatConstants.MISSED_IMS_TYPE) {
        startingTab = TAB_INDEX_MISSED;
      }
    }

    mTabTitles = new String[TAB_INDEX_COUNT];
    mTabTitles[0] = getString(R.string.call_log_all_title);
    mTabTitles[1] = getString(R.string.call_log_missed_title);

    mViewPager = (ViewPager) findViewById(R.id.call_log_pager);

    mViewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
    mViewPager.setAdapter(mViewPagerAdapter);
    mViewPager.setOffscreenPageLimit(1);
    mViewPager.setOnPageChangeListener(this);

    mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);

    mViewPagerTabs.setViewPager(mViewPager);
    mViewPager.setCurrentItem(startingTab);
  }

  @Override
  protected void onResume() {
    // Some calls may not be recorded (eg. from quick contact),
    // so we should restart recording after these calls. (Recorded call is stopped)
    PostCall.restartPerformanceRecordingIfARecentCallExist(this);
    if (!PerformanceReport.isRecording()) {
      PerformanceReport.startRecording();
    }

    mIsResumed = true;
    super.onResume();
    sendScreenViewForChildFragment();
  }

  @Override
  protected void onPause() {
    mIsResumed = false;
    super.onPause();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    final MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.call_log_options, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);
    if (mMSimCallsFragment != null && itemDeleteAll != null) {
      final CallLogAdapter adapter = mMSimCallsFragment.getAdapter();
      itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
    }

    if (mAllCallsFragment != null && itemDeleteAll != null) {
      // If onPrepareOptionsMenu is called before fragments are loaded, don't do anything.
      final CallLogAdapter adapter = mAllCallsFragment.getAdapter();
      itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty());
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (!isSafeToCommitTransactions()) {
      return true;
    }

    if (item.getItemId() == android.R.id.home) {
      PerformanceReport.recordClick(UiAction.Type.CLOSE_CALL_HISTORY_WITH_CANCEL_BUTTON);
      final Intent intent = new Intent(this, DialtactsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      return true;
    } else if (item.getItemId() == R.id.delete_all) {
      ClearCallLogDialog.show(getFragmentManager(), this);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    mViewPagerTabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
  }

  @Override
  public void onPageSelected(int position) {
    if (mIsResumed) {
      sendScreenViewForChildFragment();
    }
    mViewPagerTabs.onPageSelected(position);
  }

  @Override
  public void onPageScrollStateChanged(int state) {
    mViewPagerTabs.onPageScrollStateChanged(state);
  }

  private void sendScreenViewForChildFragment() {
    Logger.get(this).logScreenView(ScreenEvent.Type.CALL_LOG_FILTER, this);
  }

  private int getRtlPosition(int position) {
    if (ViewUtil.isRtl()) {
      return mViewPagerAdapter.getCount() - 1 - position;
    }
    return position;
  }

  private void initMSimCallLog() {
    setContentView(R.layout.call_log_activity);
    getWindow().setBackgroundDrawable(null);

    int startingTab = TAB_INDEX_MSIM;
    final Intent intent = getIntent();
    mTabTitles = new String[TAB_INDEX_COUNT_MSIM];
    if (intent != null) {
      final int callType = intent.getIntExtra(CallLog.Calls.EXTRA_CALL_TYPE_FILTER, -1);
      if (callType == CallLog.Calls.MISSED_TYPE ||
          callType == AppCompatConstants.MISSED_IMS_TYPE) {
        mMSimCallsFragment.setFilterType(Calls.MISSED_TYPE);
        mTabTitles[0] = getString(R.string.call_log_missed_title);
      }
    }

    mViewPager = (ViewPager) findViewById(R.id.call_log_pager);

    mViewPagerAdapter = new MSimViewPagerAdapter(getFragmentManager());
    mViewPager.setAdapter(mViewPagerAdapter);
    mViewPager.setOffscreenPageLimit(1);
    mViewPager.setOnPageChangeListener(this);

    mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);

    mViewPagerTabs.setViewPager(mViewPager);
    mViewPager.setCurrentItem(startingTab);
  }

  @Override
  public void callHistoryDeleted() {
    if (EnrichedCallComponent.get(this).getEnrichedCallManager().hasStoredData()) {
      Snackbar.make(
              findViewById(R.id.calllog_frame), getString(R.string.multiple_ec_data_deleted), 5_000)
          .show();
    }
  }

  @Override
  public void onBackPressed() {
    PerformanceReport.recordClick(UiAction.Type.PRESS_ANDROID_BACK_BUTTON);
    super.onBackPressed();
  }

  /** Adapter for the view pager. */
  public class ViewPagerAdapter extends FragmentPagerAdapter {

    public ViewPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public long getItemId(int position) {
      return getRtlPosition(position);
    }

    @Override
    public Fragment getItem(int position) {
      switch (getRtlPosition(position)) {
        case TAB_INDEX_ALL:
          return new CallLogFragment(
              CallLogQueryHandler.CALL_TYPE_ALL, true /* isCallLogActivity */);
        case TAB_INDEX_MISSED:
          return new CallLogFragment(Calls.MISSED_TYPE, true /* isCallLogActivity */);
        default:
          throw new IllegalStateException("No fragment at position " + position);
      }
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
      final CallLogFragment fragment = (CallLogFragment) super.instantiateItem(container, position);
      if (position == TAB_INDEX_ALL) {
          mAllCallsFragment = fragment;
      }
      return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return mTabTitles[position];
    }

    @Override
    public int getCount() {
      return TAB_INDEX_COUNT;
    }
  }

  public class MSimViewPagerAdapter extends FragmentPagerAdapter {
    public MSimViewPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int position) {
      switch (position) {
        case TAB_INDEX_MSIM:
          mMSimCallsFragment = new MSimCallLogFragment(
          CallLogQueryHandler.CALL_TYPE_ALL, true);
          return mMSimCallsFragment;
        }
      throw new IllegalStateException("No fragment at position " + position);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
      final MSimCallLogFragment fragment =
          (MSimCallLogFragment) super.instantiateItem(container, position);
        switch (position) {
          case TAB_INDEX_MSIM:
            mMSimCallsFragment = fragment;
            break;
        }
      return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
      return mTabTitles[position];
    }

    @Override
    public int getCount() {
      return TAB_INDEX_COUNT_MSIM;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == DialtactsActivity.ACTIVITY_REQUEST_CODE_CALL_DETAILS) {
      if (resultCode == RESULT_OK
          && data != null
          && data.getBooleanExtra(CallDetailsActivity.EXTRA_HAS_ENRICHED_CALL_DATA, false)) {
        String number = data.getStringExtra(CallDetailsActivity.EXTRA_PHONE_NUMBER);
        Snackbar.make(findViewById(R.id.calllog_frame), getString(R.string.ec_data_deleted), 5_000)
            .setAction(
                R.string.view_conversation,
                v -> startActivity(IntentProvider.getSendSmsIntentProvider(number).getIntent(this)))
            .setActionTextColor(getResources().getColor(R.color.dialer_snackbar_action_text_color))
            .show();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
}
