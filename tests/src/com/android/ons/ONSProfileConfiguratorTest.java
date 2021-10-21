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
    EuiccManager mMockEuiccManager;
    @Mock
    TelephonyManager mMockTelephonyManager;
    @Mock
    private Context mMockContext;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);

        doReturn(Context.EUICC_SERVICE).when(mMockContext).getSystemServiceName(EuiccManager.class);
        doReturn(Context.TELEPHONY_SERVICE).when(mMockContext)
                .getSystemServiceName(TelephonyManager.class);

        doReturn(mMockEuiccManager).when(mMockContext).getSystemService(Context.EUICC_SERVICE);
        doReturn(mMockTelephonyManager).when(mMockContext)
                .getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Test
    public void testONSProfileConfiguratorWithESIMNotSupported() {
        doReturn(false).when(mMockEuiccManager).isEnabled();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext);
        assertEquals(false, mOnsProfileConfigurator.isESIMSupported());
    }

    @Test
    public void testONSProfileConfiguratorWithESIMSupported() {
        doReturn(true).when(mMockEuiccManager).isEnabled();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext);
        assertEquals(true, mOnsProfileConfigurator.isESIMSupported());
    }

    @Test
    public void testONSProfileConfiguratorWithMultiSIMNotSupported() {
        doReturn(1).when(mMockTelephonyManager).getActiveModemCount();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext);
        assertEquals(false, mOnsProfileConfigurator.isMultiSIMPhone());
    }

    @Test
    public void testONSProfileConfiguratorWithMultiSIMSupported() {
        doReturn(2).when(mMockTelephonyManager).getActiveModemCount();

        ONSProfileConfigurator mOnsProfileConfigurator = new ONSProfileConfigurator(mMockContext);
        assertEquals(true, mOnsProfileConfigurator.isMultiSIMPhone());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
