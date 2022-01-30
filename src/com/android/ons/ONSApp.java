/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ons;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

/**
 * Top-level Application class for ONS. This is required to make sure that
 * OpportunisticNetworkService is started before any call to
 * {@link TelephonyManager#setPreferredOpportunisticDataSubscription()}. This is ensured since the
 * application is persistent meaning it will be started early on in the startup process
 */
public class ONSApp extends Application {
  private static final String TAG = "ONSApp";

  @Override
  public void onCreate() {
    if (UserHandle.myUserId() == 0) {
      ComponentName comp = new ComponentName(this.getPackageName(),
          OpportunisticNetworkService.class.getName());
      ComponentName service = this.startService(new Intent().setComponent(comp));
      if (service == null) {
        Log.d(TAG, "Could not start service " + comp.toString());
      }
    }
  }
}