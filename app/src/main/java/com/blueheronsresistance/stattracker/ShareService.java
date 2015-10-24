package com.blueheronsresistance.stattracker;


import android.app.IntentService;
import android.content.Intent;
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

    static final int BUF_SIZE = 8 * 1024; // size of BufferedInput/OutputStream

    @Override
    protected void onHandleIntent(Intent workIntent) {
        // Gets data from the incoming Intent
        String imageName = workIntent.getStringExtra("imageName");
        File imageFile = new File(new File(getCacheDir(), getString(R.string.temp_share_directory)), imageName);

        String token = workIntent.getStringExtra("token");
        String issuerUrl = workIntent.getStringExtra("issuerUrl");

        String uploadUrl = issuerUrl + getString(R.string.ocr_path, token);

        JSONObject json = uploadImage(uploadUrl, imageFile);

        JSONObject stats = checkJson(json);
        if (stats != null) {
            String submitUrl = issuerUrl + getString(R.string.submit_path, token);
            JSONObject submitStatsResponse = submitStats(submitUrl, stats);
            if (submitStatsResponse != null) {
                submitStatsResponse(submitStatsResponse);
            }
        }

        if (imageFile.delete()) {
            Log.d(TAG, "Image deleted: " + imageFile.getPath());
        } else {
            Log.e(TAG, "Failed to delete image: " + imageFile.getPath());
        }
    }

    private JSONObject uploadImage(String uploadUrl, File imageFile) {
        FileInputStream imageFIS;
        try {
            imageFIS = new FileInputStream(imageFile);
        } catch(FileNotFoundException ex) {
            uploadError("Image does not exist: " + ex.getMessage());
            return null;
        }

        int imageSize;
        try {
            imageSize = (int) imageFIS.getChannel().size();
        } catch (IOException ex) {
            uploadError("Failed getting image size, IOException: " + ex.getMessage());
            closeStream(imageFIS);
            return null;
        }
        Log.d(TAG, "Image size: " + imageSize);

        URL url;
        try {
            url =  new URL(uploadUrl); // Url to upload to with auth code substituted in
        } catch (MalformedURLException ex) {
            uploadError("URL '" + uploadUrl + "' could not be parsed: " + ex.getMessage());
            closeStream(imageFIS);
            return null;
        }

        HttpURLConnection conn;
        try {
            conn =  (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            uploadError("Failed opening connection to URL '" + uploadUrl + "', IOException: " + ex.getMessage());
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
            uploadError("Failed creating output stream, IOException: " + ex.getMessage());
            conn.disconnect();
            closeStream(imageFIS);
            return null;
        }

        BufferedInputStream imageIn = new BufferedInputStream(imageFIS);

        byte[] buf = new byte[BUF_SIZE]; // size of BufferedInput/OutputStream
        int n; // number of bytes read
        // Starting image upload
        uploadProgress(0.0);
        try {
            int totalUploaded = 0; // total bytes uploaded, used for calculating our percentage
            while ((n = imageIn.read(buf, 0, BUF_SIZE)) > 0) {
                totalUploaded += n;
                try {
                    connOut.write(buf, 0, n);
                } catch (IOException ex) {
                    uploadError("Failed sending image to server, IOException: " + ex.getMessage());
                    closeStream(connOut);
                    closeStream(imageIn);
                    conn.disconnect();
                    return null;
                }
                uploadProgress(((double) totalUploaded) / imageSize);
            }
        } catch (IOException ex) {
            uploadError("Failed reading from image, IOException: " + ex.getMessage());
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
            uploadError("Failed getting response code from connection, IOException: " + ex.getMessage());
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
                uploadError("Failed getting response data from connection, IOException: " + ex.getMessage());
                return null;
            } finally {
                closeStream(connIn);
                conn.disconnect();
            }
        } else {
            uploadError("Image upload response uploadError: " + resCode);
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
        //TODO uploadError message notification
        Log.e(TAG, error);
    }

    private void uploadProgress(Double progress) {
        Log.d(TAG, String.format("Image upload progress: %.2f%%", progress * 100));
        // TODO upload progress notification
    }

    private void ocrProgress(JSONObject json) {
        if (json.has("status")) {
            Log.d(TAG, json.optString("status"));
            // TODO notification with status message
        } else if (json.has("uploadError")) {
            //TODO uploadError message notification
            Log.e(TAG, "An uploadError occurred while processing your screenshot: " + json.optString("uploadError") + ".  Please try again or submit your stats manually.");
        } else if (!json.has("stats")) {
            //TODO uploadError message notification
            Log.e(TAG, "An unknown uploadError occurred while processing your screenshot: " + json.toString());
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
        if (json != null) {
            JSONObject stats;
            if ((stats = json.optJSONObject("stats")) != null) {
                // TODO notification with status message (separate from main notification?)
                Log.d(TAG, "Your screenshot has been processed, AP: " + stats.optInt("ap"));
                Log.d(TAG, stats.toString());
                return stats;
            } else if (json.has("uploadError")) {
                //TODO uploadError message notification
                Log.e(TAG, "An uploadError occurred after processing your screenshot: " + json.optString("uploadError") + ".  Please try again or submit your stats manually.");
            } else {
                if (json.has("session")) {
                    //TODO uploadError message notification
                    Log.e(TAG, "Your screenshot failed to process. Please try again later.  Transaction: " + json.optString("session"));
                } else {
                    //TODO uploadError message notification
                    Log.e(TAG, "Your screenshot failed to process. Please try again later.  Transaction: unknown!  Known info: " + json.toString());
                }
            }
        } else {
            //TODO uploadError message notification
            Log.e(TAG, "Screenshot processing failed");
        }
        return null;
    }

    private JSONObject submitStats(String submitUrl, JSONObject stats) {
        try {
            stats.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())); //TODO handle other dates from image filename
        } catch (JSONException e) {
            //TODO uploadError message notification
            Log.e(TAG, "submitStats failed to add date: " + e.toString());
        }
        Log.d(TAG, "submitStats stats: " + stats.toString());

        URL url;
        try {
            url =  new URL(submitUrl); // Url to upload to with auth code substituted in
        } catch (MalformedURLException ex) {
            submitError("URL '" + submitUrl + "' could not be parsed: " + ex.getMessage());
            return null;
        }

        HttpURLConnection conn;
        try {
            conn =  (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            submitError("Failed opening connection to URL '" + submitUrl + "', IOException: " + ex.getMessage());
            return null;
        }

        conn.setDoInput(true); // We want to get the response data back
        conn.setDoOutput(true); // POST request
        conn.setUseCaches(false); // No cached data
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8"); // Upload type for POST just having image data as the payload and nothing else

        OutputStream connOut;
        try {
            connOut =  conn.getOutputStream();
        } catch (IOException ex) {
            submitError("Failed creating output stream, IOException: " + ex.getMessage());
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
                encodedParams.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            Log.d(TAG, "submitStats body: " + encodedParams.toString());
            encodedByteParams = encodedParams.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException ex) {
            submitError("Encoding not supported UTF-8: " + ex.getMessage());
            return null;
        }

        try {
            connOut.write(encodedByteParams);
        } catch (IOException ex) {
            submitError("Failed sending stats to server, IOException: " + ex.getMessage());
            closeStream(connOut);
            conn.disconnect();
            return null;
        }
        closeStream(connOut); // done writing, make sure output stream is fully flushed and close since we are done with it

        int resCode;
        try {
            resCode = conn.getResponseCode();
        } catch (IOException ex) {
            submitError("Failed getting response code from connection, IOException: " + ex.getMessage());
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
                submitError("Failed getting response data from connection, IOException: " + ex.getMessage());
                return null;
            } catch (JSONException ex) {
                submitError("Failed parsing json from response data, IOException: " + ex.getMessage());
                return null;
            } finally {
                closeStream(connIn);
                conn.disconnect();
            }
        } else {
            submitError("Image upload response uploadError: " + resCode);
            conn.disconnect();
            return null;
        }
    }

    private void submitError(String error) {
        //TODO submitError message notification
        Log.e(TAG, error);
    }

    private void submitStatsResponse(JSONObject response) {
        if (response.optBoolean("uploadError")) {
            if (response.has("message")) {
                //TODO uploadError message notification
                Log.e(TAG, "submitStatsResponse uploadError: " + response.optString("message"));
            } else {
                //TODO uploadError message notification
                Log.e(TAG, "submitStatsResponse uploadError");
            }
        } else if (response.has("message")) { // We are done, hazaaa!!
            //TODO message notification
            Log.d(TAG, "submitStatsResponse: " + response.optString("message"));
        } else {
            //TODO unknown response notification
            Log.e(TAG, "submitStatsResponse unknown response: " + response.toString());
        }
    }
}
