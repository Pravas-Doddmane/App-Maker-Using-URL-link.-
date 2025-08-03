package com.devdroid.webviewtoapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
    Context mContext;

    WebAppInterface(Context c) {
        mContext = c;
    }

    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void openExternalLink(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        mContext.startActivity(browserIntent);
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        return "Android " + android.os.Build.VERSION.RELEASE + ", " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
    }
}