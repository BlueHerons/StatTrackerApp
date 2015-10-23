package com.blueheronsresistance.stattracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;


/**
 * Check image exists from intent and that the Stat Tracker token is valid
 * Then start ShareService to process image
 */
public class ShareActivity extends BaseActivity {
    private static final String TAG = "ShareActivity";

    private static final String START_SETTINGS_REQUEST = "startSettings";
    private static final String FINISH_REQUEST = "finish";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_share);

        if (savedInstanceState == null) {
            // Get the intent that started this activity
            Intent intent = getIntent();

            Log.d(TAG, "Intent type: " + intent.getType());

            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            String imageName = intent.getStringExtra("imageName");

            if (imageUri != null) {
                Log.d(TAG, "Image uri: " + imageUri.getPath());

                try {
                    getContentResolver().openInputStream(imageUri); // If we can open the image into an InputStream it exists
                    Log.d(TAG, "File exists");
                } catch (FileNotFoundException ex) {
                    Log.e(TAG, "File not found: " + ex.toString());
                    okayDialog(getString(R.string.share_error_dialog_title), getString(R.string.share_image_not_found_error_dialog), "imageNotFound", FINISH_REQUEST);
                    return;
                }
                imageName = imageToTmp(imageUri);
                if (imageName != null) {
                    checkTokenUploadImage(imageName);
                }
            } else if (imageName != null) {
                Log.d(TAG, "Image name: " + imageName);
                File imageFile = new File(new File(getCacheDir(), getString(R.string.temp_share_directory)), imageName);
                if(imageFile.exists()) {
                    Log.d(TAG, "File exists");
                    checkTokenUploadImage(imageName);
                } else {
                    Log.e(TAG, "File not found: " + imageFile.getPath());
                    okayDialog(getString(R.string.share_error_dialog_title), getString(R.string.share_image_not_found_error_dialog), "imageNotFound", FINISH_REQUEST);
                }
            } else {
                Log.e(TAG, "No image?");
                okayDialog(getString(R.string.share_error_dialog_title), getString(R.string.share_error_dialog_no_image), "noImageShared", FINISH_REQUEST);
            }
        }
    }

    private void checkTokenUploadImage(final String imageName) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        final String issuerUrl = settings.getString("issuerUrl", "");
        final String token = settings.getString("token", "");
        Log.d(TAG, issuerUrl);
        Log.d(TAG, token);
        if (issuerUrl.equals("") || token.equals("")) {
            Log.d(TAG, "No token set");
            okayDialog(getString(R.string.share_token_not_set_dialog_title), getString(R.string.share_token_not_set_dialog), "noToken", START_SETTINGS_REQUEST, imageName);
        } else {
            CheckToken check = new CheckToken() {
                @Override
                public void onCheckGood(String agentName) {
                    Log.d(TAG, "Starting sharing service");
                    startShareService(imageName, token, issuerUrl);
                }

                @Override
                public void onCheckBad(String error) {
                    Log.d(TAG, "Check token returned bad");
                    okayDialog(getString(R.string.share_check_token_fail_dialog_title), getString(R.string.share_check_token_fail_dialog) + error, "tokenBad", START_SETTINGS_REQUEST, imageName);
                }

                @Override
                public void onCheckError(String error) {
                    Log.d(TAG, "Check token returned error");
                    okayDialog(getString(R.string.share_check_token_error_dialog_title), getString(R.string.share_check_token_error_dialog) + error, "unknownAgent", START_SETTINGS_REQUEST, imageName);
                }
            };
            check.start(issuerUrl, token, getApplicationContext());
        }
    }

    private void startSettingsActivity(String imageName) {
        Intent newIntent = new Intent(this, SettingsActivity.class);
        newIntent.putExtra("imageName", imageName);
        newIntent.setType(getIntent().getType());
        startActivity(newIntent);
        finish();
    }

    private void startShareService(String imageName, String token, String issuerUrl) {
        Intent intent = new Intent(this, ShareService.class);
        intent.putExtra("imageName", imageName);
        intent.putExtra("token", token);
        intent.putExtra("issuerUrl", issuerUrl);
        startService(intent);
        Toast toast = Toast.makeText(getApplicationContext(), R.string.share_stat_upload_started_toast, Toast.LENGTH_SHORT);
        toast.show();
        finish();
    }

    public void onOkayDialogCancelOrOkay(String requestCode, String requestData) {
        Log.d(TAG, "onOkayDialogCancelOrOkay: " + requestCode);
        switch (requestCode) {
            case START_SETTINGS_REQUEST:
                startSettingsActivity(requestData);
                break;
            case FINISH_REQUEST:
                finish();
                break;
        }
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    private String imageToTmp(Uri imageUri) {
        String name = null;
        String scheme = imageUri.getScheme();
        switch (scheme) {
            case "file":
                Log.d(TAG, "Image file filename: " + imageUri.getLastPathSegment());
                name = imageUri.getLastPathSegment();
                break;
            case "content":
                Cursor cursor = getContentResolver().query(imageUri, null, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int iDisplayName = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                        name = cursor.getString(iDisplayName);
                        Log.d(TAG, "Image content filename: " + name);
                    }
                    cursor.close();
                }
        }
        if (name == null) {
            Log.d(TAG, "name is null, using unknown.tmp");
            name = "unknown.tmp";
        }
        File dir = new File(getCacheDir(), getString(R.string.temp_share_directory));
        if (!dir.mkdirs() && !dir.isDirectory()) {
            okayDialog(getString(R.string.share_error_temp_image_dialog_title), getString(R.string.share_error_tempShare_dialog), "tempShareCreation", FINISH_REQUEST);
            Log.e(TAG, "failed to make tempShare directory");
            return null;
        }

        File imageCacheFile = new File(dir, name);

        try {
            FileChannel imageFIC = ((FileInputStream) getContentResolver().openInputStream(imageUri)).getChannel();
            try {
                FileChannel imageFOC = new FileOutputStream(imageCacheFile).getChannel();
                try {
                    long bytesTransferred = 0;
                    while (bytesTransferred < imageFIC.size()) {
                        bytesTransferred += imageFOC.transferFrom(imageFIC, bytesTransferred, imageFIC.size() - bytesTransferred);
                    }
                } finally {
                    imageFOC.close();
                }
            } finally {
                imageFIC.close();
            }
            Log.d(TAG, "Image copied to: " + imageCacheFile.getPath());
            return name;
        } catch (FileNotFoundException ex) {
            Log.e(TAG, ex.getMessage());
            okayDialog(getString(R.string.share_error_temp_image_dialog_title), getString(R.string.share_error_temp_FNF_dialog), "tempFNF", FINISH_REQUEST);
            return null;
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
            okayDialog(getString(R.string.share_error_temp_image_dialog_title), getString(R.string.share_error_temp_IOE_dialog), "tempIOE", FINISH_REQUEST);
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanUpTemp();
    }
}
