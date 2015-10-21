package com.blueheronsresistance.stattracker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * Show current token name and corresponding agent
 * Allow user to delete or input a new token
 */
public class SettingsActivity extends BaseActivity implements SettingsManualInputDialogFragment.SettingsManualInputDialogListener {
    private static final String TAG = "SettingsActivity";

    private boolean deleteEnabled = false;

    private static final String STATE_AGENT_NAME = "agentName";
    private String _agentName = null;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            if(checkForStattrackerUri(intent)) {
                checkTokenFromIntent(intent);
                return;
            }
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        final String tokenName = settings.getString("tokenName", "");
        final String issuerUrl = settings.getString("issuerUrl", "");
        final String token = settings.getString("token", "");

        if (tokenName.equals("") || issuerUrl.equals("") || token.equals("")) {
            setContentView(R.layout.activity_settings_add_token);
            deleteEnabled = false;
            invalidateOptionsMenu();
        } else {
            if (savedInstanceState != null) {
                _agentName = savedInstanceState.getString(STATE_AGENT_NAME);
            }
            if (_agentName != null) {
                displayToken(_agentName, tokenName, issuerUrl);
            } else {
                setContentView(R.layout.activity_settings_loading);
                CheckToken check = new CheckToken() {
                    @Override
                    public void onCheckGood(String agentName) {
                        displayToken(agentName, tokenName, issuerUrl);
                    }

                    @Override
                    public void onCheckBad(String error) {
                        delete_token();
                        okayDialog(getString(R.string.settings_deleting_token_dialog_title), getString(R.string.settings_okay_dialog_reason) + error, "deletingToken");
                    }

                    @Override
                    public void onCheckError(String error) {
                        displayToken(getString(R.string.settings_unknown_agent_name), tokenName, issuerUrl);
                        okayDialog(getString(R.string.settings_check_token_fail_dialog_title), getString(R.string.settings_okay_dialog_reason) + error, "unknownAgent");
                    }
                };

                check.start(issuerUrl, token, getApplicationContext());
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_AGENT_NAME, _agentName);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.settings_delete_token).setEnabled(deleteEnabled);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings_scan_barcode:
                scan_barcode();
                return true;
            case R.id.settings_manual_input:
                manual_input();
                return true;
            case R.id.settings_delete_token:
                delete_token();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void displayToken(String agentName, String tokenName, String issuerUrl) {
        setContentView(R.layout.activity_settings_token);
        ((TextView) findViewById(R.id.agentName)).setText(agentName);
        ((TextView) findViewById(R.id.tokenName)).setText(tokenName);
        ((TextView) findViewById(R.id.issuerUrl)).setText(issuerUrl);

        deleteEnabled = true;
        invalidateOptionsMenu();

        _agentName = agentName;
    }

    @SuppressLint("RtlHardcoded")
    private void scan_barcode() {
        Toast toast = Toast.makeText(getApplicationContext(), R.string.settings_scan_barcode_toast, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.RIGHT, 0, 0);
        toast.show();
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.addExtra("SCAN_MODE", "QR_CODE_MODE");
        integrator.addExtra("PROMPT_MESSAGE", getString(R.string.settings_scan_barcode_message));
        integrator.initiateScan();
    }

    private void manual_input() {
        DialogFragment newFragment = new SettingsManualInputDialogFragment();
        newFragment.show(getSupportFragmentManager(), "manualInput");
    }

    private void delete_token() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.remove("tokenName");
        editor.remove("issuerUrl");
        editor.remove("token");
        editor.apply();
        setContentView(R.layout.activity_settings_add_token);

        deleteEnabled = false;
        invalidateOptionsMenu();

        _agentName = null;
    }

    @Override
    public void onSettingsManualInputDialogOkClick(String tokenName, String token, String issuerUrl) {
        checkAndSaveToken(tokenName, token, issuerUrl);
    }

    private void checkAndSaveToken(final String tokenName, final String token, final String issuerUrl) {
        final Context ctx = getApplicationContext();
        setContentView(R.layout.activity_settings_loading);
        CheckToken check = new CheckToken() {
            @Override
            public void onCheckGood(String agentName) {
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
                editor.putString("tokenName", tokenName);
                editor.putString("issuerUrl", issuerUrl);
                editor.putString("token", token);
                editor.apply();

                displayToken(agentName, tokenName, issuerUrl);

                okayDialog(getString(R.string.settings_check_token_success_dialog_title), getString(R.string.settings_check_token_success_dialog), "tokenCheckSuccess");
            }

            @Override
            public void onCheckBad(String error) {
                okayDialog(getString(R.string.settings_check_token_fail_dialog_title), getString(R.string.settings_okay_dialog_reason) + error, "tokenCheckFail");
                recreate();
            }

            @Override
            public void onCheckError(String error) {
                okayDialog(getString(R.string.settings_check_token_error_dialog_title), getString(R.string.settings_okay_dialog_reason) + error, "tokenCheckError");
                recreate();
            }
        };
        check.start(issuerUrl, token, ctx);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null && scanResult.getContents() != null) {
            Log.d(TAG, scanResult.getContents());
            Uri data = Uri.parse(scanResult.getContents());
            if (getString(R.string.app_scheme).equals(data.getScheme()) && getString(R.string.app_token_host).equals(data.getHost())) {
                checkAndSaveToken(data.getQueryParameter("name"), data.getQueryParameter("token"), data.getQueryParameter("issuer"));
            } else {
                Toast.makeText(getApplicationContext(), R.string.settings_bad_scan_barcode_toast, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkForStattrackerUri(Intent intent) {
        // uri intents will not come from history on the first time
        return (Intent.ACTION_VIEW.equals(intent.getAction()) && (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0);
    }

    private void checkTokenFromIntent(Intent intent) {
        Uri data = intent.getData();
        Log.d(TAG, data.toString());
        checkAndSaveToken(data.getQueryParameter("name"), data.getQueryParameter("token"), data.getQueryParameter("issuer"));
    }

}
