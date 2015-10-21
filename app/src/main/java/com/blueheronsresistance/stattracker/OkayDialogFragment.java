package com.blueheronsresistance.stattracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Generic dialog with a neutral okay button
 */
public class OkayDialogFragment extends DialogFragment {
    private String requestCode;
    private String requestData;

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks. */
    public interface OkayDialogListener {
        void onOkayDialogCancelOrOkay(String requestCode, String requestData);
    }

    // Use this instance of the interface to deliver action events
    private OkayDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the OkayDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the OkayDialogListener so we can send events to the host
            mListener = (OkayDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement OkayDialogListener");
        }
    }

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String title = args.getString("title");
        String message = args.getString("message");
        requestCode = args.getString("requestCode");
        requestData = args.getString("requestData");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(title)
                .setMessage(message)
                .setNeutralButton(R.string.okay_dialog_okay, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mListener.onOkayDialogCancelOrOkay(requestCode, requestData);
                    }
                });

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        mListener.onOkayDialogCancelOrOkay(requestCode, requestData);
    }
}
