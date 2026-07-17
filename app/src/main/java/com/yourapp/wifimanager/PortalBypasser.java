package com.yourapp.wifimanager;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
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

    public PortalBypasser(CaptivePortalDetector detector) {
        this.detector = detector;
    }

    public boolean autoBypass() {
        Log.i(TAG, "Starting auto bypass...");

        String portalUrl = detector.detectPortalUrl();
        if (portalUrl == null) {
            Log.i(TAG, "No portal URL detected");
            return false;
        }

        Log.i(TAG, "Portal URL: " + portalUrl);

        // Step 1: Try form submission (existing)
        boolean submitted = attemptFormSubmit(portalUrl);
        if (submitted) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            if (detector.hasInternetAccess()) {
                Log.i(TAG, "Internet access granted via form!");
                return true;
            }
        }

        // Step 2: Try clicking keyword links (handles teeba.ps style portals)
        boolean linkClicked = attemptLinkClick(portalUrl);
        if (linkClicked) {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            if (detector.hasInternetAccess()) {
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
                URL url = new URL(bypassUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                conn.disconnect();

                if (code == 200 || code == 302) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    if (detector.hasInternetAccess()) {
                        Log.i(TAG, "Bypass successful via URL: " + bypassUrl);
                        return true;
                    }
                }
            } catch (IOException ignored) {}
        }

        // Step 4: Try fetching any link that looks like a login/accept endpoint
        try {
            String html = fetchPage(portalUrl);
            if (html != null) {
                // Look for any <a> tag that might be a login/free access link
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
                    URL url = new URL(link);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setInstanceFollowRedirects(true);
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    if (code == 200 || code == 302) {
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                        if (detector.hasInternetAccess()) {
                            Log.i(TAG, "Bypass successful via discovered link: " + link);
                            return true;
                        }
                    }
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

            // Find all <a> tags with text containing keywords
            Pattern aPattern = Pattern.compile(
                "<a[^>]*href=[\"']([^\"']*)[\"'][^>]*>([\\s\\S]*?)</a>",
                Pattern.CASE_INSENSITIVE
            );
            Matcher aMatcher = aPattern.matcher(html);

            while (aMatcher.find()) {
                String href = aMatcher.group(1);
                String text = aMatcher.group(2).replaceAll("<[^>]*>", "").trim();

                if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript")) continue;

                boolean matchesKeyword = false;
                for (String keyword : KEYWORDS) {
                    if (text.contains(keyword) || href.contains(keyword)) {
                        matchesKeyword = true;
                        break;
                    }
                }
                // Also catch "click here" / "هنا" type links
                if (!matchesKeyword) {
                    String lowerText = text.toLowerCase();
                    String lowerHref = href.toLowerCase();
                    if (lowerText.contains("click") || lowerText.contains("هنا") ||
                        lowerHref.contains("login") || lowerHref.contains("free") ||
                        lowerHref.contains("guest") || lowerHref.contains("trial")) {
                        matchesKeyword = true;
                    }
                }

                if (!matchesKeyword) continue;

                // Resolve href
                String fullUrl;
                if (href.startsWith("http")) {
                    fullUrl = href;
                } else if (href.startsWith("/")) {
                    fullUrl = baseUrl + href;
                } else {
                    fullUrl = baseUrl + "/" + href;
                }

                Log.i(TAG, "Clicking link: " + fullUrl + " text=\"" + text + "\"");
                URL url = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
                int code = conn.getResponseCode();
                String finalUrl = conn.getURL().toString();
                conn.disconnect();

                Log.i(TAG, "Link response: " + code + " finalUrl=" + finalUrl);
                if (code == 200 || code == 302 || code == 301) {
                    // Check if we landed on a form page — try submitting that too
                    String redirectHtml = fetchPage(finalUrl);
                    if (redirectHtml != null && !redirectHtml.equals(html)) {
                        List<FormData> forms = extractForms(redirectHtml, baseUrl);
                        for (FormData form : forms) {
                            if (submitForm(form)) return true;
                        }
                    }
                    return true;
                }
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

            // Extract ALL forms, not just keyword-matching ones
            List<FormData> forms = extractForms(html, portalUrl);

            if (forms.isEmpty()) {
                Log.i(TAG, "No forms found");
                return false;
            }

            // Try keyword-matching forms first
            for (FormData form : forms) {
                if (!form.hasKeywordMatch) continue;
                Log.i(TAG, "Submitting keyword form to: " + form.action);
                if (submitForm(form)) return true;
            }

            // Try ALL forms with various credential combinations
            for (FormData form : forms) {
                Log.i(TAG, "Trying form to: " + form.action + " fields=" + form.fields);
                // For login forms, try common credentials
                Map<String, String> originalFields = new HashMap<>(form.fields);
                String[][] credentialSets = {
                    {"password", ""},
                    {"password", "guest"},
                    {"password", "free"},
                    {"password", "1234"},
                    {"password", "12345"},
                    {"password", "123456"},
                    {"password", "internet"},
                    {"password", "wifi"},
                    {"password", "hotspot"},
                    {"password", "user"},
                    {"password", "default"},
                    {"password", "admin"},
                    {"password", "free123"},
                    {"password", "trial"},
                    {"password", "test"},
                    {"auth_user", "", "auth_pass", ""},
                    {"auth_user", "guest", "auth_pass", "guest"},
                    {"accept", "1"},
                    {"agree", "1"}
                };
                for (String[] creds : credentialSets) {
                    form.fields = new HashMap<>(originalFields);
                    for (int i = 0; i < creds.length; i += 2) {
                        form.fields.put(creds[i], creds[i + 1]);
                    }
                    if (submitForm(form)) return true;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in form submission: " + e.getMessage());
        }

        return false;
    }

    private String fetchPage(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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

    private List<FormData> extractForms(String html, String baseUrl) {
        List<FormData> forms = new ArrayList<>();
        String base = getBaseUrl(baseUrl);

        // Pattern to find <form ...> ... </form>
        Pattern formPattern = Pattern.compile(
            "<form[^>]*(?:action=[\"']([^\"']*)[\"'])?[^>]*>([\\s\\S]*?)</form>",
            Pattern.CASE_INSENSITIVE
        );
        Matcher formMatcher = formPattern.matcher(html);

        while (formMatcher.find()) {
            String action = formMatcher.group(1);
            String formContent = formMatcher.group(2);

            // Resolve action URL (default to current page if not specified)
            if (action == null || action.isEmpty()) {
                action = baseUrl;
            } else if (!action.startsWith("http")) {
                if (action.startsWith("/")) {
                    action = base + action;
                } else {
                    action = base + "/" + action;
                }
            }

            // Check if this form contains keyword buttons
            boolean hasKeyword = false;
            for (String keyword : KEYWORDS) {
                if (formContent.contains(keyword)) {
                    hasKeyword = true;
                    break;
                }
            }

            // Extract all input fields
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

            // Also look for <button> or <a> with keywords
            Pattern buttonPattern = Pattern.compile(
                "<(?:button|a)[^>]*>([\\s\\S]*?)</(?:button|a)>",
                Pattern.CASE_INSENSITIVE
            );
            Matcher buttonMatcher = buttonPattern.matcher(formContent);

            while (buttonMatcher.find()) {
                String buttonText = buttonMatcher.group(1).replaceAll("<[^>]*>", "").trim();
                for (String keyword : KEYWORDS) {
                    if (buttonText.contains(keyword)) {
                        Log.i(TAG, "Found keyword '" + keyword + "' in button: " + buttonText);
                        break;
                    }
                }
            }

            // Look for text nodes containing keywords near submit inputs
            Pattern submitPattern = Pattern.compile(
                "<input[^>]*type=[\"']submit[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE
            );
            Matcher submitMatcher = submitPattern.matcher(formContent);
            boolean hasSubmit = submitMatcher.find();

            FormData formData = new FormData(action, fields, hasSubmit, hasKeyword);
            forms.add(formData);
            Log.i(TAG, "Found form: action=" + action + " fields=" + fields.size() + " hasSubmit=" + hasSubmit + " hasKeyword=" + hasKeyword);
        }

        return forms;
    }

    private boolean submitForm(FormData form) {
        try {
            URL url = new URL(form.action);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // Build POST data
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, String> field : form.fields.entrySet()) {
                if (postData.length() > 0) postData.append("&");
                postData.append(field.getKey()).append("=").append(URLEncoder.encode(field.getValue(), "UTF-8"));
            }
            // If no fields but has submit button, send a dummy field
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
            return responseCode == 200 || responseCode == 302 || responseCode == 204;

        } catch (IOException e) {
            Log.e(TAG, "Form submit failed: " + e.getMessage());
            return false;
        }
    }

    private String getBaseUrl(String url) {
        try {
            URL u = new URL(url);
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
        boolean hasKeywordMatch;

        FormData(String action, Map<String, String> fields, boolean hasSubmitButton, boolean hasKeywordMatch) {
            this.action = action;
            this.fields = fields;
            this.hasSubmitButton = hasSubmitButton;
            this.hasKeywordMatch = hasKeywordMatch;
        }
    }
}
