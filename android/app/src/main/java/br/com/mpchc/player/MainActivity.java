package br.com.mpchc.player;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;

/**
 * Casca Android do MPC-HC Player: carrega o mesmo index.html do programa de Windows
 * dentro de um WebView e oferece (1) seletor de arquivos e (2) gravação em Downloads.
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final String HOST = "appassets.androidplatform.net";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    // estado do arquivo que está sendo salvo pela ponte JavaScript
    private Uri saveUri;
    private OutputStream saveStream;
    private String saveName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // mantém a tela ligada durante reprodução e gravação
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF121212);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);

        // serve o index.html (pasta assets) por um endereço https local
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // bloqueia navegação para fora do aplicativo
                return !HOST.equals(request.getUrl().getHost());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
                try {
                    startActivityForResult(Intent.createChooser(intent, "Escolher áudio ou vídeo"), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new SaveBridge(), "AndroidBridge");
        webView.loadUrl("https://" + HOST + "/assets/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && filePathCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    /** Ponte usada pelo JavaScript para gravar o vídeo exportado em Downloads/MPC-HC Player. */
    private class SaveBridge {

        @JavascriptInterface
        public synchronized boolean beginSave(String name, String mime) {
            cancelSave();
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, (mime == null || mime.isEmpty()) ? "video/webm" : mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MPC-HC Player");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                saveUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (saveUri == null) {
                    return false;
                }
                saveStream = getContentResolver().openOutputStream(saveUri);
                saveName = name;
                return saveStream != null;
            } catch (Exception e) {
                cancelSave();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean writeChunk(String base64) {
            if (saveStream == null) {
                return false;
            }
            try {
                saveStream.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                cancelSave();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized String endSave() {
            if (saveStream == null || saveUri == null) {
                return "";
            }
            try {
                saveStream.close();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(saveUri, values, null, null);
                String path = "Downloads/MPC-HC Player/" + saveName;
                saveStream = null;
                saveUri = null;
                saveName = null;
                return path;
            } catch (Exception e) {
                cancelSave();
                return "";
            }
        }

        @JavascriptInterface
        public synchronized void cancelSave() {
            try {
                if (saveStream != null) {
                    saveStream.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (saveUri != null) {
                    getContentResolver().delete(saveUri, null, null);
                }
            } catch (Exception ignored) {
            }
            saveStream = null;
            saveUri = null;
            saveName = null;
        }
    }
}
