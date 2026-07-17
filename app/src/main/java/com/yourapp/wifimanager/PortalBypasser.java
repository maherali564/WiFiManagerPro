package com.yourapp.wifimanager;

import android.net.Network;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortalBypasser {
    private static final String TAG = "PortalBypasser";

    private static final String[] KEYWORDS = {
        "مجاني", "Free", "اتصال", "Connect", "دخول", "Login", "Guest",
        "مجاناً", "Free Internet", "نت مجاني", "مجانى", "guest", "free",
        "trial", "hotspot", "click here", "هنا", "تجربة", "تجريبي",
        "accept", "موافق", "أوافق", "agree", "continue", "متابعة",
        "sign in", "تسجيل", "اشتراك", "subscribe", "start", "ابدأ"
    };

    private CaptivePortalDetector detector;
    private Network wifiNetwork;

    public PortalBypasser(CaptivePortalDetector detector) {
        this.detector = detector;
    }

    public void setWifiNetwork(Network wifiNetwork) {
        this.wifiNetwork = wifiNetwork;
    }

    public boolean autoBypass() {
        Log.i(TAG, "Starting auto bypass...");

        String portalUrl = wifiNetwork != null ? detector.detectPortalUrl(wifiNetwork) : detector.detectPortalUrl();
        if (portalUrl == null) {
            Log.i(TAG, "No portal URL detected");
            return false;
        }

        Log.i(TAG, "Portal URL: " + portalUrl);

        // Step 1: Try form submission
        boolean submitted = attemptFormSubmit(portalUrl);
        if (submitted) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            if (detector.hasInternetAccess(wifiNetwork)) {
                Log.i(TAG, "Internet access granted via form!");
                return true;
            }
        }

        // Step 2: Try clicking keyword links
        boolean linkClicked = attemptLinkClick(portalUrl);
        if (linkClicked) {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            if (detector.hasInternetAccess(wifiNetwork)) {
                Log.i(TAG, "Internet access granted via link!");
                return true;
            }
        }

        // Step 3: Try common bypass paths
        String baseUrl = getBaseUrl(portalUrl);
        String[] commonBypassPaths = {
            "/login?username=guest&password=guest",
            "/status?free=1",
            "/free",
            "/guest",
            "/bypass",
            "/login?dst=&username=guest",
            "/free-trial",
            "/trial"
        };

        for (String path : commonBypassPaths) {
            try {
                String bypassUrl = baseUrl + path;
                Log.i(TAG, "Trying bypass URL: " + bypassUrl);
                int code = httpGet(bypassUrl);
                if (code == 200 || code == 302) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    if (detector.hasInternetAccess(wifiNetwork)) {
                        Log.i(TAG, "Bypass successful via URL: " + bypassUrl);
                        return true;
                    }
                }
            } catch (IOException ignored) {}
        }

        // Step 4: Scan portal page for any <a> with login/free/guest keywords
        try {
            String html = fetchPage(portalUrl);
            if (html != null) {
                Pattern linkPattern = Pattern.compile(
                    "<a[^>]*href=[\"']([^\"']*(?:login|free|guest|trial|accept|connect)[^\"']*)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE
                );
                Matcher linkMatcher = linkPattern.matcher(html);
                while (linkMatcher.find()) {
                    String link = linkMatcher.group(1);
                    if (!link.startsWith("http")) {
                        if (link.startsWith("/")) link = baseUrl + link;
                        else link = baseUrl + "/" + link;
                    }
                    Log.i(TAG, "Trying discovered link: " + link);
                    try {
                        int code = httpGet(link);
                        if (code == 200 || code == 302) {
                            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                            if (detector.hasInternetAccess(wifiNetwork)) {
                                Log.i(TAG, "Bypass successful via discovered link: " + link);
                                return true;
                            }
                        }
                    } catch (IOException ignored) {}
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning links: " + e.getMessage());
        }

        return false;
    }

    private boolean attemptLinkClick(String portalUrl) {
        try {
            String html = fetchPage(portalUrl);
            if (html == null || html.isEmpty()) return false;

            String baseUrl = getBaseUrl(portalUrl);

            Pattern aPattern = Pattern.compile(
                "<a[^>]*href=[\"']([^\"']*)[\"'][^>]*>([\\s\\S]*?)</a>",
                Pattern.CASE_INSENSITIVE
            );
            Matcher aMatcher = aPattern.matcher(html);

            while (aMatcher.find()) {
                String href = aMatcher.group(1);
                String text = aMatcher.group(2).replaceAll("<[^>]*>", "").trim();

                if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript")) continue;

                String lowerText = text.toLowerCase();
                String lowerHref = href.toLowerCase();

                boolean isTarget = lowerText.contains("free trial") || lowerText.contains("click here") ||
                                   lowerText.contains("free") || lowerText.contains("trial") ||
                                   lowerText.contains("guest") || lowerText.contains("هنا") ||
                                   lowerText.contains("تجربة") || lowerText.contains("مجاني") ||
                                   lowerText.contains("اتصال") || lowerHref.contains("free") ||
                                   lowerHref.contains("trial") || lowerHref.contains("guest") ||
                                   lowerHref.contains("login") || lowerHref.contains("connect");

                if (!isTarget) continue;

                String fullUrl;
                if (href.startsWith("http")) {
                    fullUrl = href;
                } else if (href.startsWith("/")) {
                    fullUrl = baseUrl + href;
                } else {
                    fullUrl = baseUrl + "/" + href;
                }

                Log.i(TAG, "Clicking link: " + fullUrl + " text=\"" + text + "\"");
                try {
                    int code = httpGet(fullUrl);
                    String finalUrl = getRedirectedUrl(fullUrl);
                    Log.i(TAG, "Link response: " + code + " finalUrl=" + finalUrl);
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    if (detector.hasInternetAccess(wifiNetwork)) {
                        Log.i(TAG, "Internet granted after clicking link!");
                        return true;
                    }

                    String redirectHtml = fetchPage(finalUrl);
                    if (redirectHtml != null && !redirectHtml.equals(html)) {
                        List<FormData> forms = extractForms(redirectHtml, baseUrl);
                        for (FormData form : forms) {
                            if (submitForm(form, finalUrl)) return true;
                        }
                    }
                } catch (IOException ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in link click: " + e.getMessage());
        }
        return false;
    }

    private boolean attemptFormSubmit(String portalUrl) {
        try {
            String html = fetchPage(portalUrl);
            if (html == null || html.isEmpty()) {
                Log.w(TAG, "Empty page content");
                return false;
            }

            Log.i(TAG, "Page fetched, length: " + html.length());
            List<FormData> forms = extractForms(html, portalUrl);

            if (forms.isEmpty()) {
                Log.i(TAG, "No forms found");
                return false;
            }

            for (FormData form : forms) {
                Log.i(TAG, "Submitting keyword form to: " + form.action);
                if (submitForm(form, portalUrl)) return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in form submission: " + e.getMessage());
        }
        return false;
    }

    private String fetchPage(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn;
            if (wifiNetwork != null) {
                conn = (HttpURLConnection) wifiNetwork.openConnection(url);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();
            return html.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch page: " + e.getMessage());
            return null;
        }
    }

    private int httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn;
        if (wifiNetwork != null) {
            conn = (HttpURLConnection) wifiNetwork.openConnection(url);
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }

    private String getRedirectedUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn;
            if (wifiNetwork != null) {
                conn = (HttpURLConnection) wifiNetwork.openConnection(url);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
            String finalUrl = conn.getURL().toString();
            conn.disconnect();
            return finalUrl;
        } catch (IOException e) {
            return urlString;
        }
    }

    private List<FormData> extractForms(String html, String baseUrl) {
        List<FormData> forms = new ArrayList<>();
        String base = getBaseUrl(baseUrl);

        Pattern formPattern = Pattern.compile(
            "<form[^>]*(?:action=[\"']([^\"']*)[\"'])?[^>]*>([\\s\\S]*?)</form>",
            Pattern.CASE_INSENSITIVE
        );
        Matcher formMatcher = formPattern.matcher(html);

        while (formMatcher.find()) {
            String action = formMatcher.group(1);
            String formContent = formMatcher.group(2);

            if (action == null || action.isEmpty()) {
                action = baseUrl;
            } else if (!action.startsWith("http")) {
                if (action.startsWith("/")) {
                    action = base + action;
                } else {
                    action = base + "/" + action;
                }
            }

            boolean hasKeyword = false;
            for (String keyword : KEYWORDS) {
                if (formContent.contains(keyword)) {
                    hasKeyword = true;
                    break;
                }
            }
            if (!hasKeyword) continue;

            Map<String, String> fields = new HashMap<>();
            Pattern inputPattern = Pattern.compile(
                "<input[^>]*(?:name=[\"']([^\"']*)[\"'][^>]*(?:value=[\"']([^\"']*)[\"'])?|value=[\"']([^\"']*)[\"'][^>]*(?:name=[\"']([^\"']*)[\"'])?)[^>]*>",
                Pattern.CASE_INSENSITIVE
            );
            Matcher inputMatcher = inputPattern.matcher(formContent);

            while (inputMatcher.find()) {
                String name = inputMatcher.group(1) != null ? inputMatcher.group(1) : inputMatcher.group(4);
                String value = inputMatcher.group(2) != null ? inputMatcher.group(2) : inputMatcher.group(3);
                if (name != null) {
                    fields.put(name, value != null ? value : "");
                }
            }

            Pattern submitPattern = Pattern.compile(
                "<input[^>]*type=[\"']submit[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE
            );
            Matcher submitMatcher = submitPattern.matcher(formContent);
            boolean hasSubmit = submitMatcher.find();

            FormData formData = new FormData(action, fields, hasSubmit);
            forms.add(formData);
            Log.i(TAG, "Found form: action=" + action + " fields=" + fields.size() + " hasSubmit=" + hasSubmit);
        }
        return forms;
    }

    private boolean submitForm(FormData form, String referer) {
        try {
            URL url = new URL(form.action);
            HttpURLConnection conn;
            if (wifiNetwork != null) {
                conn = (HttpURLConnection) wifiNetwork.openConnection(url);
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Referer", referer);

            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, String> field : form.fields.entrySet()) {
                if (postData.length() > 0) postData.append("&");
                postData.append(field.getKey()).append("=").append(URLEncoder.encode(field.getValue(), "UTF-8"));
            }
            if (form.fields.isEmpty()) {
                postData.append("accept=1");
            }

            Log.i(TAG, "POST data: " + postData);

            byte[] postBytes = postData.toString().getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(postBytes.length));

            OutputStream os = conn.getOutputStream();
            os.write(postBytes);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            String finalUrl = conn.getURL().toString();
            conn.disconnect();

            Log.i(TAG, "Form submit response: " + responseCode + " finalUrl=" + finalUrl);

            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            if (detector.hasInternetAccess(wifiNetwork)) {
                Log.i(TAG, "Internet granted after form submit!");
                return true;
            }

            return responseCode == 200 || responseCode == 302 || responseCode == 204;
        } catch (IOException e) {
            Log.e(TAG, "Form submit failed: " + e.getMessage());
            return false;
        }
    }

    private String getBaseUrl(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            String port = u.getPort() == -1 ? "" : ":" + u.getPort();
            return u.getProtocol() + "://" + u.getHost() + port;
        } catch (Exception e) {
            return url;
        }
    }

    private static class FormData {
        String action;
        Map<String, String> fields;
        boolean hasSubmitButton;

        FormData(String action, Map<String, String> fields, boolean hasSubmitButton) {
            this.action = action;
            this.fields = fields;
            this.hasSubmitButton = hasSubmitButton;
        }
    }
}
