package ru.maxcontrol.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "max_control_settings";
    private static final String KEY_SERVER = "server_url";
    private static final int NAVY = Color.rgb(19, 42, 67);
    private static final int GREEN = Color.rgb(46, 125, 102);
    private WebView webView;
    private EditText serverInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String saved = getPreferences(MODE_PRIVATE).getString(KEY_SERVER, "");
        if (saved.trim().isEmpty()) {
            showSetup("");
        } else {
            showBrowser(saved);
        }
    }

    private void showSetup(String message) {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        scroll.addView(root);

        TextView brand = new TextView(this);
        brand.setText("МК");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(22);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(NAVY);
        badge.setCornerRadius(dp(16));
        brand.setBackground(badge);
        root.addView(brand, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView title = new TextView(this);
        title.setText("MAX Контроль поручений");
        title.setTextColor(NAVY);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(22);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Задачи из выбранных чатов MAX — в одной хронологической ленте.");
        subtitle.setTextColor(Color.rgb(76, 91, 108));
        subtitle.setTextSize(16);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(10);
        root.addView(subtitle, subParams);

        TextView label = new TextView(this);
        label.setText("Адрес сервера приложения");
        label.setTextColor(NAVY);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(28);
        root.addView(label, labelParams);

        serverInput = new EditText(this);
        serverInput.setSingleLine(true);
        serverInput.setTextSize(15);
        serverInput.setHint("https://ваш-домен.ru/");
        serverInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        serverInput.setText(getPreferences(MODE_PRIVATE).getString(KEY_SERVER, ""));
        serverInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(Color.WHITE);
        fieldBg.setCornerRadius(dp(10));
        fieldBg.setStroke(dp(1), Color.rgb(209, 218, 228));
        serverInput.setBackground(fieldBg);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(8);
        root.addView(serverInput, inputParams);

        Button open = new Button(this);
        open.setText("Открыть мониторинг");
        open.setAllCaps(false);
        open.setTextColor(Color.WHITE);
        open.setTextSize(16);
        open.setBackground(tintedButton(GREEN));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        openParams.topMargin = dp(16);
        root.addView(open, openParams);
        open.setOnClickListener(v -> {
            String url = serverInput.getText().toString().trim();
            if (!validUrl(url)) {
                Toast.makeText(this, "Введите корректный адрес вида https://example.com/", Toast.LENGTH_LONG).show();
                return;
            }
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(serverInput.getWindowToken(), 0);
            getPreferences(MODE_PRIVATE).edit().putString(KEY_SERVER, normalizeUrl(url)).apply();
            showBrowser(normalizeUrl(url));
        });

        TextView help = new TextView(this);
        help.setText("Для работы нужен запущенный сервер MAX Контроль и настроенный бот MAX. Введите HTTPS-адрес сервера. Для локальной проверки можно использовать адрес компьютера в той же Wi-Fi сети.");
        help.setTextColor(Color.rgb(90, 103, 119));
        help.setTextSize(14);
        help.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpParams.topMargin = dp(18);
        root.addView(help, helpParams);

        if (message != null && !message.isEmpty()) {
            TextView error = new TextView(this);
            error.setText(message);
            error.setTextColor(Color.rgb(180, 45, 45));
            error.setTextSize(14);
            LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            errorParams.topMargin = dp(12);
            root.addView(error, errorParams);
        }
        setContentView(scroll);
    }

    private void showBrowser(String url) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(NAVY);
        TextView title = new TextView(this);
        title.setText("MAX Контроль");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button settings = new Button(this);
        settings.setText("Настройки");
        settings.setAllCaps(false);
        settings.setTextSize(13);
        settings.setTextColor(Color.WHITE);
        settings.setBackground(tintedButton(NAVY));
        bar.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        settings.setOnClickListener(v -> showSetup(""));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                String scheme = target.getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                            "Не удалось открыть сервер. Проверьте адрес и подключение.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        webView.loadUrl(url);
    }

    private boolean validUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("https".equalsIgnoreCase(scheme)
                    || "http".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeUrl(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private GradientDrawable tintedButton(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
