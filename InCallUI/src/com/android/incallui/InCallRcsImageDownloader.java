/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
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
import android.net.Uri;
import android.os.Handler;
import android.os.Message;

import org.codeaurora.rcscommon.FetchImageCallBack;
import org.codeaurora.rcscommon.RcsManager;

/**
 * This class InCallRcsImageDownloader is used to get the RCS shared image and
 * RCS shared location image from the RCSService. This class will call the RCSManager
 * to fetch Shared Image and Location image.
 */
public class InCallRcsImageDownloader {

    private static final int FETCH_LOCATION_IMAGE = 1;
    private static final int UPDATE_IMAGE_DETAILS = 2;

    public static interface FetchImageListener {
        public void onFetchedImage(byte[] arr);
    }

    private class ImageDetails {
        FetchImageListener mListener;
        byte[] result;
        int width, height;
        double lat, lon;

        ImageDetails(int width, int height, double lat,
            double lon, FetchImageListener listener) {
                mListener = listener;
                this.width = width;
                this.height = height;
                this.lat = lat;
                this.lon = lon;
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FETCH_LOCATION_IMAGE:
                    fetchLocationImage((ImageDetails) msg.obj);
                    break;
                case UPDATE_IMAGE_DETAILS:
                    ImageDetails details = (ImageDetails) msg.obj;
                    if (details.mListener != null) {
                        details.mListener.onFetchedImage(details.result);
                    }
                    break;
            }
        }
    };

    private Context mContext;
    private RcsManager mRcsManager;

    InCallRcsImageDownloader(Context context) {
        mContext = context;
        mRcsManager = RcsManager.getInstance(context);
    }

    /**
     * request to get the location image.
     * the image result will be returned asynchronously.
     * @param int width, int height, double lat,
     * double lon, FetchImageListener listener.
     */
    public void requestFetchLocationImage(int width, int height, double lat,
            double lon, FetchImageListener listener) {
        ImageDetails locImgDetails = new ImageDetails(width,
                height, lat, lon, listener);

        Message msg = new Message();
        msg.what = FETCH_LOCATION_IMAGE;
        msg.obj = locImgDetails;
        mHandler.sendMessage(msg);
    }

    private void fetchLocationImage(final ImageDetails details) {
        FetchImageCallBack rcsMapCallBack = new FetchImageCallBack.Stub() {
            public void onImageFetched( byte[] image) {
                Log.d(this,"onStaticMapFeatch " + image);
                details.result = image;
                Message msg = new Message();
                msg.what = UPDATE_IMAGE_DETAILS;
                msg.obj = details;
                mHandler.sendMessage(msg);
            }
        };

        mRcsManager.fetchStaticMap(details.lat, details.lon,
                    details.width, details.height, rcsMapCallBack);
    }

}
