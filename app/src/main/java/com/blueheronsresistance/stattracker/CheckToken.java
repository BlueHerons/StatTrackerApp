package com.blueheronsresistance.stattracker;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * Check if given token is valid against given server
 *  Callback one of three methods depending on success or failure
 */
class CheckToken {
    private static final String TAG = "CheckToken";

    private Context _ctx;

    public void start(String issuerUrl, String token, Context ctx) {
        Log.d(TAG, "Token: " + token);

        _ctx = ctx;

        String url = issuerUrl + ctx.getString(R.string.token_check_path, token);
        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, "CheckToken finished request");
                        checkTokenResponse(response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "CheckToken finished request with error");
                        checkTokenError(error);
                    }
                });
        Log.d(TAG, "verifyToken started request");
        // Access the RequestQueue through your singleton class.
        jsObjRequest.setShouldCache(false);
        MyVolleySingleton.getInstance(_ctx).addToRequestQueue(jsObjRequest);
    }

    private void checkTokenResponse(JSONObject response) {
        try {
            onCheckGood(response.getString("name"));
        } catch(JSONException ex) {
            onCheckError(_ctx.getString(R.string.check_token_no_agent_name));
        }
    }

    private void checkTokenError(VolleyError error) {
        if(error.networkResponse != null) {
            Log.d(TAG, "statusCode: " + Integer.toString(error.networkResponse.statusCode));
            if(error.networkResponse.statusCode == 400) {
                onCheckBad(_ctx.getString(R.string.check_token_invalid_token));
                return;
            } else if(error.networkResponse.statusCode == 403) {
                onCheckBad(_ctx.getString(R.string.check_token_token_dne));
                return;
            }
        }
        if(error.getCause() instanceof RuntimeException) {
            Log.d(TAG, error.getCause().getMessage());
            onCheckBad(error.getCause().getMessage());
            return;
        }
        onCheckError(_ctx.getString(R.string.check_token_no_response));
    }

    public void onCheckGood(String agentName) {
    }

    public void onCheckBad(String error) {
    }

    public void onCheckError(String error) {
    }
}
