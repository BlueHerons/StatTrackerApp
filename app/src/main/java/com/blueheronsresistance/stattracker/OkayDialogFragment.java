package com.blueheronsresistance.stattracker;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Generic dialog with a neutral okay button
 */
public class OkayDialogFragment extends DialogFragment {
    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String title = args.getString("title");
        String message = args.getString("message");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(title)
                .setMessage(message)
                .setNeutralButton(R.string.okay_dialog_okay, null);

        // Create the AlertDialog object and return it
        return builder.create();
    }
}
