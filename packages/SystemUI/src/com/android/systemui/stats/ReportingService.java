/*
* Copyright (C) 2014 Matricom
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.systemui.stats;;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

import com.google.analytics.tracking.android.GoogleAnalytics;
import com.google.analytics.tracking.android.ModelFields;
import com.google.analytics.tracking.android.Tracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.android.systemui.R;

public class ReportingService extends Service {
    /* package */ static final String TAG = "MatricomStats";

    private StatsUploadTask mTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        Log.d(TAG, "User has opted in -- reporting.");

        if (mTask == null || mTask.getStatus() == AsyncTask.Status.FINISHED) {
            mTask = new StatsUploadTask();
            mTask.execute();
        }

        return Service.START_REDELIVER_INTENT;
    }

    private class StatsUploadTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            String deviceId = Utilities.getUniqueID(getApplicationContext());
            String deviceName = Utilities.getDevice();
            String deviceVersion = Utilities.getModVersion();

            Log.d(TAG, "SERVICE: Device ID=" + deviceId);
            Log.d(TAG, "SERVICE: Device Name=" + deviceName);
            Log.d(TAG, "SERVICE: Device Version=" + deviceVersion);

            // report to google analytics
            GoogleAnalytics ga = GoogleAnalytics.getInstance(ReportingService.this);
            //ga.setDebug(true);
            Tracker tracker = ga.getTracker(getString(R.string.ga_trackingId));
            tracker.setAppName("Matricom");
            tracker.setAppVersion(deviceVersion);
            tracker.set(ModelFields.CLIENT_ID, deviceId);
            tracker.setCustomDimension(2, deviceName);
            tracker.setCustomMetric(1, 1L);
            tracker.sendEvent("checkin", deviceName, deviceVersion, null);
            tracker.sendView(deviceName);
            tracker.close();

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            final Context context = ReportingService.this;
            long interval;

            interval = 3L * 60L * 60L * 1000L;

            ReportingServiceManager.setAlarm(context, interval);
            stopSelf();
        }
    }
}
