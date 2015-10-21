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
public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    void okayDialog(String title, String message, String dialogId) {
        DialogFragment newFragment = new OkayDialogFragment();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        newFragment.setArguments(args);
        newFragment.show(getSupportFragmentManager(), dialogId);
    }
}
