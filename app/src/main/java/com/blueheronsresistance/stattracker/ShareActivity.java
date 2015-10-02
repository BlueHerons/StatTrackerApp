package com.blueheronsresistance.stattracker;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.FileNotFoundException;


/**
 * Handle Google login to Stat Tracker with using stored accounts on device through AccountManager
 *  Then start ShareService to process image
 */
public class ShareActivity extends Activity {
    private static final String TAG = "ShareActivity";
    /* Request code used to invoke sign in user interactions. */
    private static final int REQUEST_SIGN_IN_REQUIRED = 1001;
    private static final int REQUEST_CODE_PICK_ACCOUNT = 1000;

    private static final String STATE_INTENT_IN_PROGRESS = "mIntentInProgress";
    private static final String STATE_ACCOUNT = "account";

    /* True if we are in the process of resolving a ConnectionResult */
    private boolean mIntentInProgress = false;

    private Uri imageUri;

    private String account;

    private boolean started = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the intent that started this activity
        Intent intent = getIntent();

        imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        Log.d(TAG, "Intent type: " + intent.getType());

        if(savedInstanceState != null) {
            mIntentInProgress = savedInstanceState.getBoolean(STATE_INTENT_IN_PROGRESS, false);
            account = savedInstanceState.getString(STATE_ACCOUNT);
            started = true;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_INTENT_IN_PROGRESS, mIntentInProgress);
        outState.putString(STATE_ACCOUNT, account);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart fired");
        if (!started) {
            started = true;
            if (imageUri != null) {
                Log.d(TAG, "Image uri: " + imageUri.getPath());

                //todo move to service for decoding date from filename

                String scheme = imageUri.getScheme();
                if (scheme.equals("file")) {
                    Log.d(TAG, "Image file filename: " + imageUri.getLastPathSegment());
                }
                else if (scheme.equals("content")) {
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(imageUri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                            Log.d(TAG, "Image content filename: " + cursor.getString(columnIndex));
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                } else {
                    //unknown filename
                }

                //todo end move

                try {
                    getContentResolver().openInputStream(imageUri); // If we can open the image into an InputStream it exists
                    Log.d(TAG, "File exists");
                    //Log.d(TAG, "onStart selecting account");
                    //pickAccount();
                    // todo check if current token is valid and submit else prompt for token input
                } catch(FileNotFoundException e) {
                    Log.e(TAG, "File not found: " + e.toString());
                    //TODO error message pop up saying image not found
                    finish();
                }
            } else {
                Log.e(TAG, "No image?");
                //TODO error message pop up saying no image shared
                finish();
            }
        }
    }
/*
    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (requestCode == REQUEST_SIGN_IN_REQUIRED) {
            if (responseCode == RESULT_OK) {
                // We had to sign in - now we can finish off the token request.
                RetrieveToken();
            } else {
                // User hit cancel?
                pickAccount(); // back to pick account, can cancel whole thing out of there
            }
        } else if(requestCode == REQUEST_CODE_PICK_ACCOUNT) {
            mIntentInProgress = false;
            // Receiving a result from the AccountPicker
            if (responseCode == RESULT_OK) {
                account = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                RetrieveToken();
            } else if (responseCode == RESULT_CANCELED) {
                // The account picker dialog closed without selecting an account.
                // Notify users that they must pick an account to proceed.
                //To not? do Notify users that they must pick an account to proceed.
                finish();
            }
        }
    }

    private void pickAccount() {
        String[] accountTypes = new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE};
        Intent intent = AccountPicker.newChooseAccountIntent(null, null,
                accountTypes, false, null, null, null, null);
        startActivityForResult(intent, REQUEST_CODE_PICK_ACCOUNT);
    }

    private void RetrieveToken() {
        AccountManager accountManager  = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        if(!account.isEmpty()) {
            // find matching account
            for (final Account dAccount : accounts) {
                if (dAccount.name.equals(account)) {
                    new RetrieveTokenTask().execute(dAccount);
                    return;
                }
            }
        }
        //TODO error message pop up
        Log.e(TAG, "Error account does not exist, this shouldn't happen!?: " + account);
    }

    private class RetrieveTokenTask extends AsyncTask<Account, Void, String> {

        @Override
        protected String doInBackground(Account... params) {
            Account accountName = params[0];
            String token = null;
            try {
                token = GoogleAuthUtil.getToken(getApplicationContext(), accountName, "oauth2: " + getString(R.string.scope));
            } catch (IOException e) {
                //TODO error message pop up
                Log.e(TAG, e.getMessage());
            } catch (UserRecoverableAuthException e) {
                startActivityForResult(e.getIntent(), REQUEST_SIGN_IN_REQUIRED);
            } catch (GoogleAuthException e) {
                //TODO error message pop up
                Log.e(TAG, e.getMessage());
            }
            return token;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(s != null) { // only send once we have a token and the task hasn't thrown an exception
                verifyToken(s);
            }
        }
    }

    private void verifyToken(final String token) {
        Log.d(TAG, "Token: " + token);

        String url = Uri.parse("https://www.googleapis.com/oauth2/v1/tokeninfo").buildUpon()
                .appendQueryParameter("access_token", token)
                .build().toString();
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, "verifyToken finished request");
                        verifyTokenResponse(response, token);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "verifyToken finished request with error");
                        verifyTokenError(error, token);
                    }
                });
        Log.d(TAG, "verifyToken started request");
        // Access the RequestQueue through your singleton class.
        MyVolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsObjRequest);
    }

    private void verifyTokenResponse(JSONObject response, String token) {
        if(response.optInt("expires_in") > 30) {
            Log.d(TAG, "verifyTokenResponse token is good!");
            sendToken(token);
        } else {
            Log.d(TAG, "verifyTokenResponse token needs refreshing");
            clearToken(token);
        }
    }

    private void verifyTokenError(VolleyError error, String token) {
        if(error.networkResponse.statusCode == 400) { // we expect this if tokeninfo returns an error
            String jsonString = "";
            JSONObject response;
            try {
                jsonString = new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers));
                response = new JSONObject(jsonString);
                if (response.has("error")) {
                    Log.d(TAG, "verifyToken error: " + response.optString("error"));
                } else {
                    Log.d(TAG, "verifyToken unknown error: " + response.toString());
                }
            } catch(UnsupportedEncodingException e) { // We will just silently get a new token and try again, this shouldn't normally happen
                Log.d(TAG, "verifyToken UnsupportedEncodingException: " + e.toString());
            } catch(JSONException e) { // We will just silently get a new token and try again, we should get JSON back from this endpoint
                Log.d(TAG, "verifyToken JSONException: " + e.toString() + " | Data: " + jsonString);
            }
            clearToken(token);
        } else {
            //TODO error message pop up saying request to stat tracker failed (with error code for here and or error.toString()?)
            Log.e(TAG, "verifyToken error: " + error.toString());
        }
    }

    private void clearToken(String token) {
        new clearTokenTask().execute(token);
    }

    private class clearTokenTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String token = params[0];
            Boolean success;
            try {
                GoogleAuthUtil.clearToken(getApplicationContext(), token);
                success = true;
            } catch (IOException e) {
                //TODO error message pop up
                success = false;
                Log.e(TAG, e.getMessage());
            } catch (GooglePlayServicesAvailabilityException e) {
                success = false;
                startActivityForResult(e.getIntent(), REQUEST_SIGN_IN_REQUIRED);
            } catch (GoogleAuthException e) {
                //TODO error message pop up
                success = false;
                Log.e(TAG, e.getMessage());
            }
            return success;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            if(success) { // Only continue if we cleared the token
                RetrieveToken();
            }
        }
    }

    private void sendToken(String token) {
        String url = Uri.parse(getString(R.string.base_url) + getString(R.string.token_path)).buildUpon()
            .appendQueryParameter("token", token)
            .build().toString();

        Log.d(TAG, "Built Url: " + url);

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
            (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                Log.d(TAG, "sendToken finished request");
                sendTokenResponse(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                //TODO error message pop up saying request to stat tracker failed (with error code for here and or error.toString()?)
                Log.e(TAG, "sendToken error: " + error.toString());
                }
            });
        Log.d(TAG, "sendToken started request");
        // Access the RequestQueue through your singleton class.
        MyVolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsObjRequest);
    }

    private void sendTokenResponse(JSONObject response) {
        String status;
        Log.d(TAG, "sendTokenResponse Response: " + response.toString());

        if (response.has("status")) {
            status = response.optString("status");
            switch (status) {
                case "authentication_required":
                    Log.e(TAG, "sendTokenResponse failed to login");
                    //TODO error message pop up saying login failed
                    break;
                case "okay":
                    Log.d(TAG, "sendTokenResponse okay");
                    try {
                        Intent intent = new Intent(this, ShareService.class);
                        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
                        intent.putExtra("authCode", response.getJSONObject("agent").getString("auth_code"));
                        startService(intent); // todo startForeground ?
                        finish();
                    } catch(JSONException e) {
                        Log.e(TAG, "sendTokenResponse missing agent auth_code: " + e.toString());
                        //TODO error message pop up saying JSONException error
                    }
                    break;
                case "registration_required":
                    Log.d(TAG, "sendTokenResponse register");
                    //TODO error message pop up saying registration required (with details from server?)
                    break;
                default:
                    //TODO error message pop up saying login status response from Stat Tracker unknown: status
                    break;
            }
        } else {
            Log.e(TAG, "sendTokenResponse missing status property in response");
            //TODO error message pop up saying missing status property in response
        }
    }*/
}
