/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens for incoming OTP codes via the SmsRetriever API and surfaces
 * them as a suggestion in the keyboard's suggestion strip.
 *
 * How it works:
 * 1. When the keyboard starts (onStartInput), we call SmsRetriever.startSmsRetriever()
 * 2. SmsRetriever listens for SMS that contain the app hash (no SMS permission needed)
 * 3. When an OTP SMS arrives, we extract the numeric code
 * 4. The code is surfaced via the OtpListener callback to show in suggestion strip
 * 5. Tapping the suggestion inserts the OTP code
 *
 * Note: SmsRetriever requires Google Play Services. If unavailable, this
 * provider gracefully does nothing — the keyboard works normally without it.
 */
public class OtpSuggestionProvider {

    private static final String TAG = "OtpSuggestion";

    /** Pattern to extract numeric OTP codes (4-8 digits) */
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{4,8})\\b");

    /** How long an OTP suggestion stays visible (60 seconds) */
    private static final long OTP_DISPLAY_TIMEOUT_MS = 60_000;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private BroadcastReceiver mSmsReceiver;
    private String mCurrentOtp;
    private OtpListener mListener;
    private boolean mIsListening;

    public interface OtpListener {
        /** Called when an OTP code is detected from SMS. */
        void onOtpDetected(String otpCode);
        /** Called when the OTP suggestion should be cleared (timeout or used). */
        void onOtpCleared();
    }

    public OtpSuggestionProvider(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * Set the listener that will receive OTP events.
     */
    public void setOtpListener(OtpListener listener) {
        mListener = listener;
    }

    /**
     * Start listening for OTP SMS. Call this from onStartInput.
     * Safe to call multiple times.
     */
    public void startListening() {
        if (mIsListening) return;

        try {
            SmsRetrieverClient client = SmsRetriever.getClient(mContext);
            client.startSmsRetriever()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "SmsRetriever started");
                        registerReceiver();
                        mIsListening = true;
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "SmsRetriever not available (no Play Services?)", e);
                        // Graceful degradation — keyboard works without OTP detection
                    });
        } catch (Exception e) {
            // Google Play Services not available — ignore silently
            Log.w(TAG, "Could not start SmsRetriever", e);
        }
    }

    /**
     * Stop listening for OTP SMS. Call this from onFinishInput or onDestroy.
     */
    public void stopListening() {
        if (!mIsListening) return;
        mIsListening = false;

        unregisterReceiver();
        clearOtp();
    }

    /**
     * Returns the currently detected OTP code, or null if none.
     */
    public String getCurrentOtp() {
        return mCurrentOtp;
    }

    /**
     * Called when the user taps the OTP suggestion to insert it.
     * Clears the OTP after use.
     */
    public void consumeOtp() {
        clearOtp();
    }

    private void registerReceiver() {
        if (mSmsReceiver != null) return;

        mSmsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) return;

                Bundle extras = intent.getExtras();
                if (extras == null) return;

                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                if (status == null) return;

                if (status.getStatusCode() == CommonStatusCodes.SUCCESS) {
                    String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                    if (message != null) {
                        extractAndSurfaceOtp(message);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mContext.registerReceiver(mSmsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            mContext.registerReceiver(mSmsReceiver, filter);
        }
    }

    private void unregisterReceiver() {
        if (mSmsReceiver != null) {
            try {
                mContext.unregisterReceiver(mSmsReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered
            }
            mSmsReceiver = null;
        }
    }

    /**
     * Extract numeric OTP from SMS body and surface it.
     */
    private void extractAndSurfaceOtp(String smsBody) {
        Matcher matcher = OTP_PATTERN.matcher(smsBody);
        if (matcher.find()) {
            String otp = matcher.group(1);
            mCurrentOtp = otp;

            Log.d(TAG, "OTP detected: " + otp.charAt(0) + "***");

            if (mListener != null) {
                mListener.onOtpDetected(otp);
            }

            // Auto-clear after timeout
            mHandler.removeCallbacksAndMessages(null);
            mHandler.postDelayed(this::clearOtp, OTP_DISPLAY_TIMEOUT_MS);
        }
    }

    private void clearOtp() {
        mCurrentOtp = null;
        mHandler.removeCallbacksAndMessages(null);
        if (mListener != null) {
            mListener.onOtpCleared();
        }
    }

    /**
     * Releases resources. Call from LatinIME.onDestroy().
     */
    public void destroy() {
        stopListening();
        mListener = null;
    }
}
