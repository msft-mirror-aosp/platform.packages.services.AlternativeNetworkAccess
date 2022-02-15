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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;
import android.util.Log;
import android.util.Pair;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ONSProfileDownloaderTest extends ONSBaseTest {
    private static final String TAG = ONSProfileDownloaderTest.class.getName();
    private static final int TEST_SUB_ID = 1;
    private static final String TEST_SMDP_ADDRESS = "LPA:1$TEST-ESIM.COM$";

    @Mock
    Context mMockContext;
    @Mock
    SubscriptionInfo mMockSubInfo;
    @Mock
    private ONSProfileConfigurator mMockONSProfileConfig;
    @Mock
    EuiccManager mMockEUICCManager;
    @Mock
    ONSProfileDownloader.IONSProfileDownloaderListener mMockDownloadListener;

    @Before
    public void setUp() throws Exception {
        super.setUp("ONSTest");
        MockitoAnnotations.initMocks(this);
        doReturn(mMockEUICCManager).when(mMockONSProfileConfig).getEuiccManager();
    }

    static class WorkerThread extends Thread {
        Looper mWorkerLooper;
        private final Runnable mRunnable;

        WorkerThread(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void run() {
            super.run();
            Looper.prepare();
            mWorkerLooper = Looper.myLooper();
            mRunnable.run();
            mWorkerLooper.loop();
        }

        public void exit() {
            mWorkerLooper.quitSafely();
        }
    }

    @Test
    public void testNoInternetDownloadRequest() {
        doReturn(false).when(mMockONSProfileConfig).isInternetConnectionAvailable();
        doReturn(TEST_SUB_ID).when(mMockSubInfo).getSubscriptionId();

        Looper.prepare();
        ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mMockContext,
                mMockONSProfileConfig, null);
        onsProfileDownloader.downloadOpportunisticESIM(mMockSubInfo);

        verify(mMockONSProfileConfig).setRetryDownloadWhenConnectedFlag(true);
        verify(mMockEUICCManager, never()).downloadSubscription(null, true, null);
    }

    @Test
    public void testNullSMDPAddress() {
        doReturn(true).when(mMockONSProfileConfig).isInternetConnectionAvailable();
        doReturn(null).when(mMockONSProfileConfig).getSMDPServerAddress(TEST_SUB_ID);
        doReturn(TEST_SUB_ID).when(mMockSubInfo).getSubscriptionId();

        Looper.prepare();
        ONSProfileDownloader onsProfileDownloader =
                new ONSProfileDownloader(mMockContext, mMockONSProfileConfig, null);
        onsProfileDownloader.downloadOpportunisticESIM(mMockSubInfo);

        verify(mMockEUICCManager, never()).downloadSubscription(null, true, null);
    }

    @Test
    public void testDownloadSuccessCallback() {

        final Object lock = new Object();
        final ONSProfileDownloader.IONSProfileDownloaderListener mListener =
                new ONSProfileDownloader.IONSProfileDownloaderListener() {
                    @Override
                    public void onDownloadComplete(int primarySubId) {
                        assertEquals(primarySubId, TEST_SUB_ID);
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                };

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader =
                        new ONSProfileDownloader(mMockContext, mMockONSProfileConfig, mListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_DOWNLOAD);

                ONSProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureUnresolvableError() {
        doReturn(2).when(mMockONSProfileConfig).getDownloadRetryMaxAttemptsVal(TEST_SUB_ID);
        doReturn(1).when(mMockONSProfileConfig).getDownloadRetryBackOffTimerVal(TEST_SUB_ID);
        doReturn(mMockEUICCManager).when(mMockONSProfileConfig).getEuiccManager();
        doReturn(true).when(mMockONSProfileConfig).isInternetConnectionAvailable();
        doReturn(TEST_SMDP_ADDRESS).when(mMockONSProfileConfig).getSMDPServerAddress(TEST_SUB_ID);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockONSProfileConfig, mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);

                ONSProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        verifyZeroInteractions(mMockEUICCManager);
        workerThread.exit();
    }

    @Test
    public void testDownloadFailureMemoryFullError() {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader =
                        new ONSProfileDownloader(mMockContext, mMockONSProfileConfig,
                                mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_DOWNLOAD);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_EUICC_INSUFFICIENT_MEMORY);

                ONSProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        verify(mMockONSProfileConfig).deleteOpportunisticSubscriptions(TEST_SUB_ID);
        workerThread.exit();
    }

    @Test
    public void testDownloadFailureConnectionError() {

        doReturn(2).when(mMockONSProfileConfig).getDownloadRetryMaxAttemptsVal(TEST_SUB_ID);
        doReturn(1).when(mMockONSProfileConfig).getDownloadRetryBackOffTimerVal(TEST_SUB_ID);
        doReturn(mMockEUICCManager).when(mMockONSProfileConfig).getEuiccManager();
        doReturn(true).when(mMockONSProfileConfig).isInternetConnectionAvailable();
        doReturn(TEST_SMDP_ADDRESS).when(mMockONSProfileConfig).getSMDPServerAddress(TEST_SUB_ID);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockONSProfileConfig, mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_CONNECTION_ERROR);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_DOWNLOAD);

                ONSProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        //After first download error, next download will be triggered between 1 & 2*
        //CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT(1sec for testing)
        //Should take less than 2 secs for download re-attempt.
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String testActCode = DownloadableSubscription.forActivationCode(TEST_SMDP_ADDRESS)
                .getEncodedActivationCode();

        verify(mMockEUICCManager).downloadSubscription(
                argThat((DownloadableSubscription ds) ->
                        ds.getEncodedActivationCode().equals(testActCode)),
                eq(true), any(PendingIntent.class));

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureTimeout() {

        doReturn(2).when(mMockONSProfileConfig).getDownloadRetryMaxAttemptsVal(TEST_SUB_ID);
        doReturn(1).when(mMockONSProfileConfig).getDownloadRetryBackOffTimerVal(TEST_SUB_ID);
        doReturn(mMockEUICCManager).when(mMockONSProfileConfig).getEuiccManager();
        doReturn(true).when(mMockONSProfileConfig).isInternetConnectionAvailable();
        doReturn(TEST_SMDP_ADDRESS).when(mMockONSProfileConfig).getSMDPServerAddress(TEST_SUB_ID);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockONSProfileConfig, mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_DOWNLOAD);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_TIME_OUT);

                ONSProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        //After first download error, next download will be triggered between 1 & 2*
        //CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT(1sec for testing)
        //Should take less than 2 secs for download re-attempt.
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String testActCode = DownloadableSubscription.forActivationCode(TEST_SMDP_ADDRESS)
                .getEncodedActivationCode();

        verify(mMockEUICCManager).downloadSubscription(
                argThat((DownloadableSubscription ds) ->
                        ds.getEncodedActivationCode().equals(testActCode)),
                eq(true), any(PendingIntent.class));

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureOperationBusy() {

        doReturn(2).when(mMockONSProfileConfig).getDownloadRetryMaxAttemptsVal(TEST_SUB_ID);
        doReturn(1).when(mMockONSProfileConfig).getDownloadRetryBackOffTimerVal(TEST_SUB_ID);
        doReturn(mMockEUICCManager).when(mMockONSProfileConfig).getEuiccManager();
        doReturn(true).when(mMockONSProfileConfig).isInternetConnectionAvailable();
        doReturn(TEST_SMDP_ADDRESS).when(mMockONSProfileConfig).getSMDPServerAddress(TEST_SUB_ID);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockONSProfileConfig, mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_DOWNLOAD);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_OPERATION_BUSY);

                ONSProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        //After first download error, next download will be triggered between 1 & 2*
        //CarrierConfigManager.KEY_ESIM_DOWNLOAD_RETRY_BACKOFF_TIMER_SEC_INT(1sec for testing)
        //Should take less than 2 secs for download re-attempt.
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        String testActCode = DownloadableSubscription.forActivationCode(TEST_SMDP_ADDRESS)
                .getEncodedActivationCode();

        verify(mMockEUICCManager).downloadSubscription(
                argThat((DownloadableSubscription ds) ->
                        ds.getEncodedActivationCode().equals(testActCode)),
                eq(true), any(PendingIntent.class));

        workerThread.exit();
    }

    @Test
    public void testDownloadFailureInvalidResponse() {

        doReturn(2).when(mMockONSProfileConfig).getDownloadRetryMaxAttemptsVal(TEST_SUB_ID);
        doReturn(1).when(mMockONSProfileConfig).getDownloadRetryBackOffTimerVal(TEST_SUB_ID);
        doReturn(mMockEUICCManager).when(mMockONSProfileConfig).getEuiccManager();
        doReturn(true).when(mMockONSProfileConfig).isInternetConnectionAvailable();
        doReturn(TEST_SMDP_ADDRESS).when(mMockONSProfileConfig).getSMDPServerAddress(TEST_SUB_ID);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockONSProfileConfig, mMockDownloadListener);

                Intent intent = new Intent(mContext, ONSProfileResultReceiver.class);
                intent.setAction(ONSProfileResultReceiver.ACTION_ONS_RESULT_CALLBACK);
                intent.putExtra(Intent.EXTRA_COMPONENT_NAME, ONSProfileDownloader.class.getName());
                intent.putExtra(ONSProfileDownloader.PARAM_PRIMARY_SUBID, TEST_SUB_ID);
                intent.putExtra(ONSProfileDownloader.PARAM_REQUEST_TYPE,
                        ONSProfileDownloader.REQUEST_CODE_DOWNLOAD_SUB);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE,
                        EuiccManager.OPERATION_DOWNLOAD);
                intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE,
                        EuiccManager.ERROR_INVALID_RESPONSE);

                ONSProfileDownloader.onCallbackIntentReceived(intent,
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR);
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        verifyZeroInteractions(mMockEUICCManager);
        workerThread.exit();
    }

    @Test
    public void testCalculateBackoffDelay() {
        final Object lock = new Object();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockONSProfileConfig, mMockDownloadListener);
                ONSProfileDownloader.DownloadHandler downloadHandler =
                        onsProfileDownloader.new DownloadHandler();

                int delay = downloadHandler.calculateBackoffDelay(1, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay <= 2));

                Log.i(TAG, "calculateBackoffDelay(2, 1)");
                delay = downloadHandler.calculateBackoffDelay(2, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay < 4));

                delay = downloadHandler.calculateBackoffDelay(3, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay < 8));

                delay = downloadHandler.calculateBackoffDelay(4, 1) / 1000;
                assertEquals(true, (delay >= 1 && delay < 16));

                delay = downloadHandler.calculateBackoffDelay(1, 2) / 1000;
                assertEquals(true, (delay >= 1 && delay <= 4));

                delay = downloadHandler.calculateBackoffDelay(1, 3) / 1000;
                assertEquals(true, (delay >= 1 && delay <= 6));

                delay = downloadHandler.calculateBackoffDelay(2, 2) / 1000;
                assertEquals(true, (delay >= 2 && delay < 8));

                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        workerThread.exit();
    }

    @Test
    public void testDownloadOpCode() {
        final Object lock = new Object();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ONSProfileDownloader onsProfileDownloader = new ONSProfileDownloader(mContext,
                        mMockONSProfileConfig, mMockDownloadListener);
                ONSProfileDownloader.DownloadHandler downloadHandler =
                        onsProfileDownloader.new DownloadHandler();

                ONSProfileDownloader.DownloadRetryOperationCode res =
                        downloadHandler.getOperationCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0,
                        EuiccManager.OPERATION_DOWNLOAD, 0);
                assertEquals(
                        ONSProfileDownloader.DownloadRetryOperationCode.DOWNLOAD_SUCCESSFUL, res);

                res = downloadHandler.getOperationCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_DOWNLOAD,
                        EuiccManager.ERROR_EUICC_INSUFFICIENT_MEMORY);
                assertEquals(ONSProfileDownloader.DownloadRetryOperationCode
                        .DELETE_INACTIVE_OPP_ESIM_IF_EXISTS, res);

                res = downloadHandler.getOperationCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_DOWNLOAD,
                        EuiccManager.ERROR_TIME_OUT);
                assertEquals(ONSProfileDownloader.DownloadRetryOperationCode
                        .RETRY_AFTER_BACKOFF_TIME, res);

                res = downloadHandler.getOperationCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_DOWNLOAD,
                        EuiccManager.ERROR_CONNECTION_ERROR);
                assertEquals(ONSProfileDownloader.DownloadRetryOperationCode
                        .RETRY_AFTER_BACKOFF_TIME, res);

                res = downloadHandler.getOperationCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR, 0,
                        EuiccManager.OPERATION_DOWNLOAD,
                        EuiccManager.ERROR_INVALID_RESPONSE);
                assertEquals(ONSProfileDownloader.DownloadRetryOperationCode
                        .STOP_RETRY_UNTIL_SIM_STATE_CHANGE, res);

                res = downloadHandler.getOperationCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, 0,
                        EuiccManager.OPERATION_DOWNLOAD,
                        EuiccManager.ERROR_INVALID_RESPONSE);
                assertEquals(ONSProfileDownloader.DownloadRetryOperationCode
                        .STOP_RETRY_UNTIL_SIM_STATE_CHANGE, res);

                res = downloadHandler.getOperationCode(
                        EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_ERROR, 0xA810048,
                        EuiccManager.OPERATION_SMDX_SUBJECT_REASON_CODE, 0);
                assertEquals(ONSProfileDownloader.DownloadRetryOperationCode
                        .DELETE_INACTIVE_OPP_ESIM_IF_EXISTS, res);

                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        workerThread.exit();
    }

    @Test
    public void testSMDPErrorParsing() {
        final Object lock = new Object();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Pair<String, String> res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA8B1051);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //8B1 -> 8.11.1
                //051 -> 5.1
                assertEquals("8.11.1", res.first);
                assertEquals("5.1", res.second);

                res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA810061);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //810 -> 8.1.0
                //061 -> 6.1
                assertEquals("8.1.0", res.first);
                assertEquals("6.1", res.second);

                res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA810048);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //810 -> 8.1.0
                //048 -> 4.8
                assertEquals("8.1.0", res.first);
                assertEquals("4.8", res.second);

                res = ONSProfileDownloader
                        .decodeSmdxSubjectAndReasonCode(0xA8B1022);
                //0A->OPERATION_SMDX_SUBJECT_REASON_CODE
                //8B1 -> 8.11.1
                //022 -> 2.2
                assertEquals("8.11.1", res.first);
                assertEquals("2.2", res.second);

                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };

        WorkerThread workerThread = new WorkerThread(runnable);
        workerThread.start();

        synchronized (lock) {
            try {
                lock.wait();
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        workerThread.exit();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
