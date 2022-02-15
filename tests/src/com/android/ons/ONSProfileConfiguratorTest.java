/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.mockito.Mockito.doReturn;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ONSProfileConfiguratorTest extends ONSBaseTest {
    private static final String TAG = ONSProfileConfiguratorTest.class.getName();
    @Mock
    private Context mMockContext;
    @Mock
    SubscriptionManager mMockSubManager;
    @Mock
    EuiccManager mMockEuiccManager;
    @Mock
    TelephonyManager mMockTelephonyManager;
    @Mock
    private ONSProfileActivator mMockONSProfileActivator;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testESIMNotSupported() {
        doReturn(false).when(mMockEuiccManager).isEnabled();
        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext,
                mMockSubManager, mMockEuiccManager, mMockTelephonyManager);
        assertEquals(false, mOnsProfileConfigurator.isESIMSupported());
    }

    @Test
    public void testESIMSupported() {
        doReturn(true).when(mMockEuiccManager).isEnabled();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext,
                mMockSubManager, mMockEuiccManager, mMockTelephonyManager);
        assertEquals(true, mOnsProfileConfigurator.isESIMSupported());
    }

    @Test
    public void testMultiSIMNotSupported() {
        doReturn(1).when(mMockTelephonyManager).getActiveModemCount();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext,
                mMockSubManager, mMockEuiccManager, mMockTelephonyManager);
        assertEquals(false, mOnsProfileConfigurator.isMultiSIMPhone());
    }

    @Test
    public void testMultiSIMSupported() {
        doReturn(2).when(mMockTelephonyManager).getSupportedModemCount();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext,
                mMockSubManager, mMockEuiccManager, mMockTelephonyManager);
        assertEquals(true, mOnsProfileConfigurator.isMultiSIMPhone());
    }

    /* This test case needs application context instead of mock Context. Need to investigate how to
    get application in Junit test class.
    @Test
    public void testRetryDownloadAfterRebootFlagSaving() {
        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(
                Context.getApplicationContext(), mMockSubManager, mMockEuiccManager,
                mMockTelephonyManager);

        mOnsProfileConfigurator.setRetryDownloadAfterReboot(true, 1);
        assertEquals(true, mOnsProfileConfigurator.getRetryDownloadAfterReboot());
        assertEquals(1, mOnsProfileConfigurator.getRetryDownloadpSIMSubId());

        mOnsProfileConfigurator.setRetryDownloadAfterReboot(false, 1);
        assertEquals(false, mOnsProfileConfigurator.getRetryDownloadAfterReboot());
        assertEquals(1, mOnsProfileConfigurator.getRetryDownloadpSIMSubId());

        mOnsProfileConfigurator.setRetryDownloadAfterReboot(true, 2);
        assertEquals(true, mOnsProfileConfigurator.getRetryDownloadAfterReboot());
        assertEquals(1, mOnsProfileConfigurator.getRetryDownloadpSIMSubId());

        mOnsProfileConfigurator.setRetryDownloadAfterReboot(false, 2);
        assertEquals(false, mOnsProfileConfigurator.getRetryDownloadAfterReboot());
        assertEquals(1, mOnsProfileConfigurator.getRetryDownloadpSIMSubId());
    }*/

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
