package com.blueheronsresistance.stattracker;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.File;

/**
 * Class for other Activities to be extended from
 *  Also holds common functions
 */
public abstract class BaseActivity extends AppCompatActivity implements OkayDialogFragment.OkayDialogListener {
    private static final String TAG = "BaseActivity";

    void okayDialog(String title, String message, String dialogId, String requestCode, String requestData) {
        DialogFragment newFragment = new OkayDialogFragment();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        args.putString("requestCode", requestCode);
        args.putString("requestData", requestData);
        newFragment.setArguments(args);
        newFragment.show(getSupportFragmentManager(), dialogId);
    }

    void okayDialog(String title, String message, String dialogId, String requestCode) {
        okayDialog(title, message, dialogId, requestCode, null);
    }

    void okayDialog(String title, String message, String dialogId) {
        okayDialog(title, message, dialogId, null, null);
    }

    protected void cleanUpTemp() {
        Log.d(TAG, "Cleaning tempShare dir");
        File dir = new File(getCacheDir(), getString(R.string.temp_share_directory));
        if (dir.isDirectory()) {
            Long currentTime = System.currentTimeMillis();
            for (File file : dir.listFiles()) {
                if (file.lastModified() + 30 * 60 * 1000 < currentTime) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted: " + file.getPath());
                    } else {
                        Log.e(TAG, "Failed to delete: " + file.getPath());
                    }
                } else {
                    Log.d(TAG, "Kept: " + file.getPath());
                }
            }
            Log.d(TAG, "Done cleaning tempShare dir");
        } else {
            Log.d(TAG, "No tempShare dir");
        }
    }
}
