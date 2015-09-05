package com.blueheronsresistance.stattracker;


import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Service used for uploading image to the Stat Tracker and submitting stats
 */
public class ShareService extends IntentService {
    public ShareService() {
        super("ShareService");
    }
    private static final String TAG = "ShareService";

    private static String authCode;

    @Override
    protected void onHandleIntent(Intent workIntent) {
        // Gets data from the incoming Intent
        Uri imageUri = workIntent.getParcelableExtra(Intent.EXTRA_STREAM);

        authCode = workIntent.getStringExtra("authCode");

        Log.d(TAG, "Started with authCode: " + authCode);

        new sendImageTask().execute(imageUri);
    }

    private class sendImageTask extends AsyncTask<Uri, Double, Boolean> {
        static final int BUF_SIZE = 8*1024; // size of BufferedInput/OutputStream
        JSONObject json = null;
        @Override
        protected Boolean doInBackground(Uri... uris) {
            int totalUploaded = 0; // total bytes uploaded, used for calculating our percentage
            boolean success = true; // start with true, if anything fails, set to false
            try { // TODO this probably needs to be broken into smaller try/catch blocks for better error reporting
                FileInputStream imageFIS = (FileInputStream) getContentResolver().openInputStream(uris[0]);
                int imageSize = (int) imageFIS.getChannel().size();
                Log.d(TAG, "Image size: " + imageSize);

                URL url = new URL(getString(R.string.base_url) + String.format(getString(R.string.ocr_path), authCode)); // Url to upload to with auth code substituted in
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setDoInput(true); // We want to get the response data back
                conn.setDoOutput(true); // POST request
                conn.setUseCaches(false); // No cached data
                conn.setFixedLengthStreamingMode(imageSize);  // image size in bytes
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); // Upload type for POST just having image data as the payload and nothing else
                //conn.setSSLSocketFactory(pinnedSSLSocketFactory.getSocketFactory(getApplicationContext())); // Setup SSL with our pinned cert

                BufferedInputStream imageIn = new BufferedInputStream(imageFIS);
                BufferedOutputStream connOut = new BufferedOutputStream(conn.getOutputStream());

                byte[] buf = new byte[BUF_SIZE]; // size of BufferedInput/OutputStream
                int n; // number of bytes read
                // Starting image upload
                publishProgress(0.0);
                while((n = imageIn.read(buf, 0, BUF_SIZE)) > 0) {
                    totalUploaded += n;
                    connOut.write(buf, 0, n);
                    publishProgress(((double) totalUploaded) / imageSize);
                }
                connOut.close(); // done writing, make sure output stream is fully flushed and close since we are done with it

                if(conn.getResponseCode() == 200) {
                    Log.d(TAG, "Image upload 200 response");
                    InputStreamReader connIn = new InputStreamReader(conn.getInputStream());

                    StringBuilder response = new StringBuilder();
                    char[] cBuf = new char[BUF_SIZE]; // size of InputStreamReader buffer
                    while((n = connIn.read(cBuf, 0, BUF_SIZE)) > 0) {
                        response.append(cBuf, 0, n);
                        json = parseOCRResponse(response.toString());
                        publishProgress(1.0);
                    }
                    connIn.close();
                } else {
                    success = false;
                    Log.e(TAG, "Image upload response error: " + conn.getResponseCode());
                }
                conn.disconnect();
            } catch (Exception e) {
                success = false;
                Log.e(TAG, "doInBackground Exception: " + e.toString());
            }

            return success;
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //TODO notification of pre upload
            Log.d(TAG, "Image Upload starting");
        }
        @Override
        protected void onProgressUpdate(Double... progress) {
            super.onProgressUpdate(progress);
            if(progress[0] < 1) {
                Log.d(TAG, String.format("Image upload progress: %.2f%%", progress[0]*100));
                // upload progress notification
            } else {
                if(json != null) {
                    if (json.has("status")) {
                        Log.d(TAG, json.optString("status"));
                        // TODO notification with status message
                    } else if (json.has("error")) {
                        //TODO error message notification
                        Log.e(TAG, "An error occurred while processing your screenshot: " + json.optString("error") + ".  Please try again or submit your stats manually.");
                    } else if(!json.has("stats")) {
                        //TODO error message notification
                        Log.e(TAG, "An unknown error occurred while processing your screenshot: " + json.toString());
                    }
                } else {
                    Log.d(TAG, "Image upload finished");
                }
            }
        }

        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            // check response is good then
            if(result) {
                JSONObject stats;
                if ((stats = json.optJSONObject("stats")) != null) {
                    // TODO notification with status message
                    Log.d(TAG, "Your screenshot has been processed, AP: " + stats.optInt("ap"));
                    Log.d(TAG, stats.toString());
                    submitStats(stats);
                } else if (json.has("error")) {
                    //TODO error message notification
                    Log.e(TAG, "An error occurred after processing your screenshot: " + json.optString("error") + ".  Please try again or submit your stats manually.");
                } else {
                    if(json.has("session")) {
                        //TODO error message notification
                        Log.e(TAG, "Your screenshot failed to process. Please try again later.  Transaction: " + json.optString("session"));
                    } else {
                        //TODO error message notification
                        Log.e(TAG, "Your screenshot failed to process. Please try again later.  Transaction: unknown!");
                    }
                }
            } else {
                //TODO error message notification
                Log.e(TAG, "Screenshot processing failed");
            }
        }

        private JSONObject parseOCRResponse(String response) {
            String[] split = response.split("\n\n");
            String jsonStr = split[split.length - 1];
            try {
                if (jsonStr.endsWith("\n")) {
                    return new JSONObject(jsonStr.trim());
                } else if (split.length > 1) {
                    return new JSONObject(split[split.length - 1].trim());
                } else {
                    return new JSONObject();
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                return new JSONObject();
            }
        }
    }

    private void submitStats(final JSONObject stats) {
        try {
            stats.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        } catch (JSONException e) {
            //TODO error message notification
            Log.e(TAG, "submitStats failed to add date: " + e.toString());
        }
        Log.d(TAG, "submitStats stats: " + stats.toString());

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
            (Request.Method.POST, getString(R.string.base_url) + String.format(getString(R.string.submit_path), authCode), null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "submitStats finished request");
                    submitStatsResponse(response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    //TODO error message notification (with error code for here and or error.toString()?)
                    Log.e(TAG, "submitStats error: " + error.toString());
                }
            }) {
            @Override
            public byte[] getBody() {
                Map<String, String> params = new HashMap<>();
                StringBuilder encodedParams = new StringBuilder();
                String paramsEncoding = getParamsEncoding();
                String key;
                Iterator<String> iter = stats.keys();
                while(iter.hasNext()) {
                    key = iter.next();
                    params.put(key, stats.optString(key));
                }
                try {
                    for (Map.Entry<String, String> entry : params.entrySet()) {
                        if(encodedParams.length() > 0) {
                            encodedParams.append('&');
                        }
                        encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                        encodedParams.append('=');
                        encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                    }
                    Log.d(TAG, "submitStats body: " + encodedParams.toString());
                    return encodedParams.toString().getBytes(paramsEncoding);
                } catch (UnsupportedEncodingException uee) {
                    throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
                }
            }
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=" + getParamsEncoding();
            }
        };
        Log.d(TAG, "submitStats started request");
        // Access the RequestQueue through your singleton class.
        MyVolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsObjRequest);
    }

    private void submitStatsResponse(JSONObject response) {
        if (response.optBoolean("error")) {
            if (response.has("message")) {
                //TODO error message notification
                Log.e(TAG, "submitStatsResponse error: " + response.optString("message"));
            } else {
                //TODO error message notification
                Log.e(TAG, "submitStatsResponse error");
            }
        } else if (response.has("message")) { // We are done, hazaaa!!
            //TODO message notification
            Log.d(TAG, "submitStatsResponse: " + response.optString("message"));
        } else {
            //TODO unknown response notification
            Log.e(TAG, "submitStatsResponse unknown response");
        }
    }
}