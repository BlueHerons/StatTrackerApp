package com.blueheronsresistance.stattracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.widget.EditText;

/**
 * Created by kevin on 9/23/15.
 */
public class SettingsManualInputDialogFragment extends DialogFragment {

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks. */
    public interface SettingsManualInputDialogListener {
        void onSettingsManualInputDialogOkClick(String tokenName, String token, String issuerUrl);
    }

    // Use this instance of the interface to deliver action events
    SettingsManualInputDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the SettingsManualInputDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the SettingsManualInputDialogListener so we can send events to the host
            mListener = (SettingsManualInputDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement SettingsManualInputDialogListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();

        builder.setTitle(R.string.settings_manual_input_dialog_title)
                .setView(inflater.inflate(R.layout.dialog_settings_manual_input, null))
                .setPositiveButton(R.string.settings_manual_input_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Send the positive button event back to the host activity

                        String tokenName = ((EditText) getDialog().findViewById(R.id.settings_manual_input_dialog_name)).getText().toString();
                        String token = ((EditText) getDialog().findViewById(R.id.settings_manual_input_dialog_value)).getText().toString();
                        String issuerUrl = ((EditText) getDialog().findViewById(R.id.settings_manual_input_dialog_url)).getText().toString();
                        mListener.onSettingsManualInputDialogOkClick(tokenName, token, issuerUrl);
                    }
                })
                .setNegativeButton(R.string.settings_manual_input_dialog_cancel, null);

        // Create the AlertDialog object and return it
        return builder.create();
    }
}
