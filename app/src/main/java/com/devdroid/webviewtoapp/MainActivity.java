package com.devdroid.webviewtoapp;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar pgBar;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private long downloadReference;
    private BroadcastReceiver downloadReceiver;

    // WEB_URL is now a non-static String field, to be initialized from strings.xml
    private String WEB_URL;
    private static final String TAG = "WebViewApp";

    private final String[] DANGEROUS_PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
    };

    // To handle permission requests from the web content
    private PermissionRequest webPermissionRequest;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        pgBar = findViewById(R.id.pgbar);

        // Retrieve the URL from strings.xml and assign it to WEB_URL
        WEB_URL = getString(R.string.URL);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initWebView();
        registerDownloadReceiver();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            checkPermissionsAndLoad();
        }
    }

    private void initWebView() {
        WebSettings webSettings = webView.getSettings();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setAllowFileAccessFromFileURLs(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setGeolocationEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkImage(false);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setSupportZoom(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(true);
        }

        webView.setWebViewClient(new AdvancedWebViewClient());
        webView.setWebChromeClient(new AdvancedWebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] mediaPermissions = {
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                    android.Manifest.permission.READ_MEDIA_AUDIO,
                    android.Manifest.permission.POST_NOTIFICATIONS
            };
            requestPermissions(DANGEROUS_PERMISSIONS, mediaPermissions);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] legacyPermissions = {
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            requestPermissions(DANGEROUS_PERMISSIONS, legacyPermissions);
        } else {
            webView.loadUrl(WEB_URL);
        }
    }

    private void requestPermissions(String[] basePermissions, String[] extraPermissions) {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : basePermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        for (String permission : extraPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            webView.loadUrl(WEB_URL);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadReference == referenceId) {
                    Toast.makeText(MainActivity.this, "Download completed", Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private class AdvancedWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            pgBar.setVisibility(View.VISIBLE);
            pgBar.setProgress(0);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pgBar.setVisibility(View.GONE);
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            pgBar.setVisibility(View.GONE);
            if (request.isForMainFrame()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    showErrorPage(error.getDescription().toString());
                }
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // WARNING: This is a security risk. In a production app, you should not
            // blindly proceed. Instead, show a warning to the user.
            handler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(view, request.getUrl().toString());
        }

        private boolean handleUrl(WebView view, String url) {
            // Check if the URL is from the same domain as your app.
            // If it's a link to a different site, open it in an external app.
            if (!url.contains(Uri.parse(WEB_URL).getHost())) {
                try {
                    Intent externalIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(externalIntent);
                    return true;
                } catch (ActivityNotFoundException e) {
                    // Fallback to loading the URL in the WebView if no external app can handle it.
                    Toast.makeText(MainActivity.this, "No application can handle this request, please install a web browser.", Toast.LENGTH_LONG).show();
                    view.loadUrl(url);
                    return true;
                }
            }

            // Handle specific protocols like tel, mailto, and whatsapp within the app
            if (url.startsWith("tel:")) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
                return true;
            } else if (url.startsWith("mailto:")) {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url)));
                return true;
            } else if (url.startsWith("whatsapp:")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (url.startsWith("intent:")) {
                try {
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error handling intent URL", e);
                }
                return true;
            } else if (url.endsWith(".pdf") || url.endsWith(".doc") || url.endsWith(".docx") ||
                    url.endsWith(".xls") || url.endsWith(".xlsx") || url.endsWith(".ppt") ||
                    url.endsWith(".pptx")) {
                downloadFile(url);
                return true;
            }
            // For all other links, load them within the WebView
            view.loadUrl(url);
            return true;
        }
    }

    private class AdvancedWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;
            Intent intent = fileChooserParams.createIntent();
            try {
                fileChooserLauncher.launch(intent);
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                Toast.makeText(MainActivity.this, "No file manager available", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            pgBar.setProgress(newProgress);
            if (newProgress == 100) {
                pgBar.setVisibility(View.GONE);
            } else {
                pgBar.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webPermissionRequest = request;
                List<String> permissionsToRequest = new ArrayList<>();
                String[] resources = request.getResources();
                for (String resource : resources) {
                    if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        permissionsToRequest.add(android.Manifest.permission.CAMERA);
                    } else if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO);
                    }
                }
                if (!permissionsToRequest.isEmpty()) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            permissionsToRequest.toArray(new String[0]),
                            PERMISSION_REQUEST_CODE);
                } else {
                    request.grant(request.getResources());
                }
            }
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, false);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePathCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ClipData clipData = result.getData().getClipData();
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else {
                        results = new Uri[]{result.getData().getData()};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    private void downloadFile(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("File Download");
        request.setDescription("Downloading file...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, null, null));

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadReference = downloadManager.enqueue(request);
    }

    private void showErrorPage(String error) {
        String errorHtml = "<html><body style='text-align:center; background-color:#f0f0f0; padding-top:50px;'>" +
                "<h1>Error Loading Page</h1><p>" + error + "</p>" +
                "<button onclick=\"window.location.reload()\">Retry</button>" +
                "</body></html>";
        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null);
    }

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
        public String getDeviceInfo() {
            return "Android " + Build.VERSION.RELEASE + ", " + Build.MANUFACTURER + " " + Build.MODEL;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (webPermissionRequest != null) {
                    webPermissionRequest.grant(webPermissionRequest.getResources());
                    webPermissionRequest = null;
                } else {
                    webView.loadUrl(WEB_URL);
                }
            } else {
                if (webPermissionRequest != null) {
                    webPermissionRequest.deny();
                    webPermissionRequest = null;
                }
                Toast.makeText(this, "Some features may not work without permissions", Toast.LENGTH_LONG).show();
                webView.loadUrl(WEB_URL);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}