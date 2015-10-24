package com.blueheronsresistance.stattracker;


import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Service used for uploading image to the Stat Tracker and submitting stats
 */
public class ShareService extends IntentService {
    public ShareService() {
        super("ShareService");
    }

    private static final String TAG = "ShareService";

    private static final String ENCODING = "UTF-8";

    private static final int NOTIFY_PROGRESS_ID = 1;
    private static final int NOTIFY_STATUS_ID = 2;
    private static final int NOTIFY_UPLOAD_ERROR_ID = 3;

    static final int BUF_SIZE = 8 * 1024; // size of BufferedInput/OutputStream

    @Override
    protected void onHandleIntent(Intent workIntent) {
        NotificationCompat.Builder mBuilder = getNotificationBuilder(getString(R.string.service_success_notification_progress_title), getString(R.string.service_success_notification_progress_start))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        startForeground(NOTIFY_PROGRESS_ID, mBuilder.build());

        // Gets data from the incoming Intent
        String imageName = workIntent.getStringExtra(getString(R.string.intent_extra_image_name));
        File imageFile = new File(new File(getCacheDir(), getString(R.string.temp_share_directory)), imageName);

        String token = workIntent.getStringExtra(getString(R.string.intent_extra_token));
        String issuerUrl = workIntent.getStringExtra(getString(R.string.intent_extra_issuer_url));

        String uploadUrl = issuerUrl + getString(R.string.ocr_path, token);

        JSONObject json = uploadImage(uploadUrl, imageFile);
        if (json != null) {
            JSONObject stats = checkJson(json);
            if (stats != null) {
                String submitUrl = issuerUrl + getString(R.string.submit_path, token);
                JSONObject submitStatsResponse = submitStats(submitUrl, stats);
                if (submitStatsResponse != null) {
                    String response = submitStatsResponse(submitStatsResponse);
                    if (response != null) {
                        String dashboardUrl = issuerUrl + getString(R.string.dashboard_path);
                        statusNotification(getString(R.string.service_success_notification_finished_title), getString(R.string.service_success_notification_finished_ap, response, stats.optInt("ap")), dashboardUrl);
                    }
                }
            }
        }

        if (imageFile.delete()) {
            Log.d(TAG, "Image deleted: " + imageFile.getPath());
        } else {
            Log.e(TAG, "Failed to delete image: " + imageFile.getPath());
        }

        stopForeground(true);
    }

    private JSONObject uploadImage(String uploadUrl, File imageFile) {
        FileInputStream imageFIS;
        try {
            imageFIS = new FileInputStream(imageFile);
        } catch(FileNotFoundException ex) {
            uploadError(getString(R.string.service_error_upload_image_dne) + ex.getMessage());
            return null;
        }

        int imageSize;
        try {
            imageSize = (int) imageFIS.getChannel().size();
        } catch (IOException ex) {
            uploadError(getString(R.string.service_error_upload_image_size) + ex.getMessage());
            closeStream(imageFIS);
            return null;
        }
        Log.d(TAG, "Image size: " + imageSize);

        URL url;
        try {
            url =  new URL(uploadUrl); // Url to upload to with auth code substituted in
        } catch (MalformedURLException ex) {
            uploadError(getString(R.string.service_error_upload_url_parse, uploadUrl, ex.getMessage()));
            closeStream(imageFIS);
            return null;
        }

        HttpURLConnection conn;
        try {
            conn =  (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            uploadError(getString(R.string.service_error_upload_url_connect, uploadUrl, ex.getMessage()));
            closeStream(imageFIS);
            return null;
        }

        conn.setDoInput(true); // We want to get the response data back
        conn.setDoOutput(true); // POST request
        conn.setUseCaches(false); // No cached data
        conn.setFixedLengthStreamingMode(imageSize);  // image size in bytes
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); // Upload type for POST just having image data as the payload and nothing else

        OutputStream connOut;
        try {
            connOut =  conn.getOutputStream();
        } catch (IOException ex) {
            uploadError(getString(R.string.service_error_upload_output_stream) + ex.getMessage());
            conn.disconnect();
            closeStream(imageFIS);
            return null;
        }

        BufferedInputStream imageIn = new BufferedInputStream(imageFIS);

        byte[] buf = new byte[BUF_SIZE]; // size of BufferedInput/OutputStream
        int n; // number of bytes read
        // Starting image upload
        uploadProgress(imageSize, 0);
        try {
            int totalUploaded = 0; // total bytes uploaded, used for calculating our percentage
            while ((n = imageIn.read(buf, 0, BUF_SIZE)) > 0) {
                totalUploaded += n;
                try {
                    connOut.write(buf, 0, n);
                } catch (IOException ex) {
                    uploadError(getString(R.string.service_error_upload_sending_image) + ex.getMessage());
                    closeStream(connOut);
                    closeStream(imageIn);
                    conn.disconnect();
                    return null;
                }
                uploadProgress(imageSize, totalUploaded);
            }
        } catch (IOException ex) {
            uploadError(getString(R.string.service_error_upload_reading_image) + ex.getMessage());
            closeStream(connOut);
            closeStream(imageIn);
            conn.disconnect();
            return null;
        }
        closeStream(connOut); // done writing, make sure output stream is fully flushed and close since we are done with it
        closeStream(imageIn);

        int resCode;
        try {
            resCode = conn.getResponseCode();
        } catch (IOException ex) {
            uploadError(getString(R.string.service_error_upload_response_code_fail) + ex.getMessage());
            return null;
        }

        if (resCode == 200) {
            Log.d(TAG, "Image upload 200 response");
            InputStreamReader connIn = null;
            try {
                connIn = new InputStreamReader(conn.getInputStream());

                StringBuilder response = new StringBuilder();
                JSONObject json = null;
                char[] cBuf = new char[BUF_SIZE]; // size of InputStreamReader buffer
                while ((n = connIn.read(cBuf, 0, BUF_SIZE)) > 0) {
                    response.append(cBuf, 0, n);
                    json = parseOCRResponse(response.toString());
                    ocrProgress(json);
                }
                return json;
            } catch (IOException ex) {
                uploadError(getString(R.string.service_error_upload_response_data) + ex.getMessage());
                return null;
            } finally {
                closeStream(connIn);
                conn.disconnect();
            }
        } else {
            uploadError(getString(R.string.service_error_upload_response_code_invalid) + resCode);
            conn.disconnect();
            return null;
        }
    }

    private void closeStream(InputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException ex) {
            Log.e(TAG, "Failed closing InputStream: " + ex.getMessage());
        }
    }

    private void closeStream(OutputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException ex) {
            Log.e(TAG, "Failed closing OutputStream: " + ex.getMessage());
        }
    }

    private void closeStream(InputStreamReader stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException ex) {
            Log.e(TAG, "Failed closing OutputStream: " + ex.getMessage());
        }
    }

    private void uploadError(String error) {
        errorNotification(getString(R.string.service_error_upload_error_title), error);
    }

    private void uploadProgress(int max, int progress) {
        Log.d(TAG, String.format("Image upload progress: %.2f%%", ((double) progress/max) * 100));
        NotificationCompat.Builder mBuilder = getNotificationBuilder(getString(R.string.service_success_notification_progress_title), getString(R.string.service_success_notification_progress_upload))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(max, progress, false);
        startForeground(NOTIFY_PROGRESS_ID, mBuilder.build());
    }

    private void ocrProgress(JSONObject json) {
        if (json.has("status")) {
            Log.d(TAG, json.optString("status"));
            NotificationCompat.Builder mBuilder = getNotificationBuilder(getString(R.string.service_success_notification_progress_title), json.optString("status"))
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setProgress(0, 0, true);
            startForeground(NOTIFY_PROGRESS_ID, mBuilder.build());
        }
    }

    private JSONObject parseOCRResponse(String response) {
        String[] split = response.split("\n\n");
        String jsonStr = split[split.length - 1];
        try {
            if (jsonStr.endsWith("\n")) {
                return new JSONObject(jsonStr.trim());
            } else if (split.length > 1) {
                return new JSONObject(split[split.length - 2].trim());
            } else {
                return new JSONObject();
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            return new JSONObject();
        }
    }

    private JSONObject checkJson(JSONObject json) {
        // check response is good then
        JSONObject stats;
        if ((stats = json.optJSONObject("stats")) != null) {
            Log.d(TAG, "Your screenshot has been processed, AP: " + stats.optInt("ap"));
            Log.d(TAG, stats.toString());
            NotificationCompat.Builder mBuilder = getNotificationBuilder(getString(R.string.service_success_notification_progress_title), getString(R.string.service_success_upload_ap, stats.optInt("ap")))
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS);
            startForeground(NOTIFY_PROGRESS_ID, mBuilder.build());
            return stats;
        } else if (json.has("uploadError")) {
            uploadError(getString(R.string.service_error_upload_json_upload_error, json.optString("uploadError")));
        } else {
            if (json.has("session")) {
                uploadError(getString(R.string.service_error_upload_json_session) + json.optString("session"));
            } else {
                uploadError(getString(R.string.service_error_upload_json_no_session) + json.toString());
            }
        }
        return null;
    }

    private JSONObject submitStats(String submitUrl, JSONObject stats) {
        try {
            stats.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())); //TODO handle other dates from image filename
        } catch (JSONException e) {
            submitError(getString(R.string.service_error_submit_date) + e.toString());
        }
        Log.d(TAG, "submitStats stats: " + stats.toString());

        URL url;
        try {
            url =  new URL(submitUrl); // Url to upload to with auth code substituted in
        } catch (MalformedURLException ex) {
            submitError(getString(R.string.service_error_submit_url_parse, submitUrl, ex.getMessage()));
            return null;
        }

        HttpURLConnection conn;
        try {
            conn =  (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            submitError(getString(R.string.service_error_submit_url_connect, submitUrl, ex.getMessage()));
            return null;
        }

        conn.setDoInput(true); // We want to get the response data back
        conn.setDoOutput(true); // POST request
        conn.setUseCaches(false); // No cached data
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=" + ENCODING); // Upload type for POST just having image data as the payload and nothing else

        OutputStream connOut;
        try {
            connOut =  conn.getOutputStream();
        } catch (IOException ex) {
            submitError(getString(R.string.service_error_submit_output_stream) + ex.getMessage());
            conn.disconnect();
            return null;
        }

        Map<String, String> params = new HashMap<>();
        StringBuilder encodedParams = new StringBuilder();
        String key;
        Iterator<String> iter = stats.keys();
        byte[] encodedByteParams;
        while (iter.hasNext()) {
            key = iter.next();
            params.put(key, stats.optString(key));
        }
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (encodedParams.length() > 0) {
                    encodedParams.append('&');
                }
                encodedParams.append(URLEncoder.encode(entry.getKey(), ENCODING));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), ENCODING));
            }
            Log.d(TAG, "submitStats body: " + encodedParams.toString());
            encodedByteParams = encodedParams.toString().getBytes(ENCODING);
        } catch (UnsupportedEncodingException ex) {
            submitError(getString(R.string.service_error_submit_encoding, ENCODING, ex.getMessage()));
            return null;
        }

        try {
            connOut.write(encodedByteParams);
        } catch (IOException ex) {
            submitError(getString(R.string.service_error_submit_sending_stats) + ex.getMessage());
            closeStream(connOut);
            conn.disconnect();
            return null;
        }
        closeStream(connOut); // done writing, make sure output stream is fully flushed and close since we are done with it

        int resCode;
        try {
            resCode = conn.getResponseCode();
        } catch (IOException ex) {
            submitError(getString(R.string.service_error_submit_response_code_fail) + ex.getMessage());
            return null;
        }

        if (resCode == 200) {
            Log.d(TAG, "Stat submission 200 response");
            InputStreamReader connIn = null;
            try {
                connIn = new InputStreamReader(conn.getInputStream());

                int n; // number of bytes read
                StringBuilder response = new StringBuilder();
                char[] cBuf = new char[BUF_SIZE]; // size of InputStreamReader buffer
                while ((n = connIn.read(cBuf, 0, BUF_SIZE)) > 0) {
                    response.append(cBuf, 0, n);
                }
                Log.d(TAG, "submitStats finished request");
                return new JSONObject(response.toString());
            } catch (IOException ex) {
                submitError(getString(R.string.service_error_submit_response_data) + ex.getMessage());
                return null;
            } catch (JSONException ex) {
                submitError(getString(R.string.service_error_submit_response_json) + ex.getMessage());
                return null;
            } finally {
                closeStream(connIn);
                conn.disconnect();
            }
        } else {
            submitError(getString(R.string.service_error_submit_response_code_invalid) + resCode);
            conn.disconnect();
            return null;
        }
    }

    private void submitError(String error) {
        errorNotification(getString(R.string.service_error_submit_error_title), error);
    }

    private String submitStatsResponse(JSONObject response) {
        if (response.optBoolean("uploadError")) {
            if (response.has("message")) {
                submitError(getString(R.string.service_error_submit_json_upload_error_message) + response.optString("message"));
            } else {
                submitError(getString(R.string.service_error_submit_json_upload_error_no_message) + response.toString());
            }
        } else if (response.has("message")) { // We are done, hazaaa!!
            return response.optString("message");
        } else {
            submitError(getString(R.string.service_error_submit_json_unknown) + response.toString());
        }
        return null;
    }

    private void errorNotification(String title, String text) {
        Log.e(TAG, text);
        NotificationCompat.Builder mBuilder = getNotificationBuilder(title, text)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0))
                .setCategory(NotificationCompat.CATEGORY_ERROR);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFY_UPLOAD_ERROR_ID, mBuilder.build());
    }

    private void statusNotification(String title, String text, String url) {
        Log.d(TAG, text);
        Uri uri = Uri.parse(url);
        NotificationCompat.Builder mBuilder = getNotificationBuilder(title, text)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(Intent.ACTION_VIEW, uri), 0))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text));

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFY_STATUS_ID, mBuilder.build());
    }

    private NotificationCompat.Builder getNotificationBuilder(String title, String text) {
        return new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(text);
    }
}
