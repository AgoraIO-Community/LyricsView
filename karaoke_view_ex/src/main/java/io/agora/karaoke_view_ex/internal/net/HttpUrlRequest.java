package io.agora.karaoke_view_ex.internal.net;


import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.internal.config.Config;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;

/**
 * HTTP request handler for downloading data.
 * Provides methods for making HTTP GET and POST requests with support for streaming responses.
 */
public class HttpUrlRequest {
    private static final String TAG = Constants.TAG + "-HttpURLRequest";

    /**
     * Callback for HTTP request events
     */
    private RequestCallback mCallback;

    /**
     * Flag to indicate if the request has been cancelled
     */
    private volatile boolean mCancelled;

    /**
     * Default constructor
     */
    public HttpUrlRequest() {
        mCancelled = false;
    }

    /**
     * Sets the callback for HTTP request events
     *
     * @param callback The callback to set
     */
    public void setCallback(RequestCallback callback) {
        this.mCallback = callback;
    }

    /**
     * Sets the cancelled state of the request
     *
     * @param cancelled true to cancel the request, false otherwise
     */
    public void setCancelled(boolean cancelled) {
        this.mCancelled = cancelled;
    }

    /**
     * Performs an HTTP POST request
     *
     * @param urlStr          The URL to request
     * @param requestProperty Map of request headers
     * @param writeData       Data to send in the request body
     * @param isStream        Whether to handle the response as a stream
     */
    public void requestPostUrl(String urlStr, Map<String, String> requestProperty, String writeData, boolean isStream) {
        LogUtils.d("http requestPostUrl urlStr:" + urlStr + ",requestProperty:" + requestProperty + ", writeData:" + writeData);
        InputStream is = null;
        StringBuilder responseContent = new StringBuilder();
        try {
            urlStr = Uri.encode(urlStr, "\"-![.:/,%?&=]\"");
            URL url = new URL(urlStr);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(Constants.HTTP_TIMEOUT * 1000);
            conn.setReadTimeout(Constants.HTTP_TIMEOUT * 1000);

            conn.setRequestMethod("POST");
            if (null != requestProperty) {
                for (Map.Entry<String, String> entry : requestProperty.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            conn.setDoOutput(true);

            conn.setDoInput(true);

            if (!TextUtils.isEmpty(writeData)) {
                OutputStream os = conn.getOutputStream();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    os.write(writeData.getBytes(StandardCharsets.UTF_8));
                } else {
                    os.write(writeData.getBytes(Constants.UTF_8));
                }
                os.flush();
                os.close();
            }

            int code = conn.getResponseCode();

            if (Config.DEBUG) {
                // Get server response headers
                Map<String, List<String>> headers = conn.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    String name = entry.getKey();
                    for (String value : entry.getValue()) {
                        LogUtils.d("http response header:" + name + " : " + value);
                    }
                }
            }

            if (HttpURLConnection.HTTP_OK == code) {
                is = conn.getInputStream();

                byte[] bytes = new byte[1024 * 4];
                int len = 0;
                int total = conn.getContentLength();
                int sum = 0;
                while ((len = is.read(bytes)) != -1) {
                    if (mCancelled) {
                        break;
                    }
                    if (len == 0) {
                        continue;
                    }
                    sum += len;
                    if (isStream) {
                        if (null != mCallback) {
                            mCallback.updateResponseData(bytes, len, sum, total);
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            responseContent.append(new String(bytes, 0, len, StandardCharsets.UTF_8));
                        } else {
                            responseContent.append(new String(bytes, 0, len, Constants.UTF_8));
                        }
                    }
                }
                if (!isStream) {
                    if (null != mCallback) {
                        mCallback.onHttpResponse(responseContent.toString());
                    }
                }

                if (null != mCallback) {
                    mCallback.requestFinish();
                }
            } else {
                is = conn.getErrorStream();
                BufferedReader ir = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    ir = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                } else {
                    ir = new BufferedReader(new InputStreamReader(is, Constants.UTF_8));
                }
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = ir.readLine()) != null) {
                    result.append(line);
                }
                if (null != mCallback) {
                    mCallback.requestFail(code, result.toString());
                }
                ir.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (null != mCallback) {
                if (e instanceof java.net.SocketTimeoutException) {
                    mCallback.requestFail(Constants.ERROR_HTTP_TIMEOUT, e.getMessage());
                } else if (e instanceof java.net.UnknownHostException) {
                    mCallback.requestFail(Constants.ERROR_HTTP_UNKNOWN_HOST, e.getMessage());
                } else if (e instanceof java.net.ConnectException) {
                    mCallback.requestFail(Constants.ERROR_HTTP_NOT_CONNECT, e.getMessage());
                } else {
                    mCallback.requestFail(Constants.ERROR_GENERAL, e.getMessage());
                }
            }
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Performs an HTTP GET request
     *
     * @param urlStr  The URL to request
     * @param headers Map of request headers
     */
    public void requestGetUrl(String urlStr, Map<String, String> headers) {
        LogUtils.d("http requestGetUrl urlStr:" + urlStr + ",headers:" + headers);
        try {
            URL url = new URL(urlStr);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(Constants.HTTP_TIMEOUT * 1000);
            conn.setReadTimeout(Constants.HTTP_TIMEOUT * 1000);

            conn.setRequestMethod("GET");
            if (null != headers) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            int code = conn.getResponseCode();

            if (Config.DEBUG) {
                // Get server response headers
                Map<String, List<String>> responseHeaders = conn.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                    String name = entry.getKey();
                    for (String value : entry.getValue()) {
                        LogUtils.d("http response header:" + name + " : " + value);
                    }
                }
            }

            if (HttpURLConnection.HTTP_OK == code) {
                InputStream is = conn.getInputStream();
                int total = conn.getContentLength();
                byte[] bytes = new byte[2048];
                int len = 0;
                int sum = 0;
                while ((len = is.read(bytes)) != -1) {
                    if (mCancelled) {
                        break;
                    }
                    if (len == 0) {
                        continue;
                    }
                    sum += len;
                    if (null != mCallback) {
                        mCallback.updateResponseData(bytes, len, sum, total);
                    }
                }

                if (-1 == total) {
                    // Just for server not supporting content-length
                    if (null != mCallback) {
                        mCallback.updateResponseData(null, 0, sum, sum);
                    }
                }

                is.close();

                if (null != mCallback) {
                    mCallback.requestFinish();
                }
            } else {
                InputStream is = conn.getErrorStream();
                BufferedReader ir = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    ir = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                } else {
                    ir = new BufferedReader(new InputStreamReader(is, Constants.UTF_8));
                }
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = ir.readLine()) != null) {
                    result.append(line);
                }
                if (null != mCallback) {
                    mCallback.requestFail(code, result.toString());
                }
                ir.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.d("http response " + e);
            if (null != mCallback) {
                if (e instanceof java.net.SocketTimeoutException) {
                    mCallback.requestFail(Constants.ERROR_HTTP_TIMEOUT, e.getMessage());
                } else if (e instanceof java.net.UnknownHostException) {
                    mCallback.requestFail(Constants.ERROR_HTTP_UNKNOWN_HOST, e.getMessage());
                } else if (e instanceof java.net.ConnectException) {
                    mCallback.requestFail(Constants.ERROR_HTTP_NOT_CONNECT, e.getMessage());
                } else {
                    mCallback.requestFail(Constants.ERROR_GENERAL, e.getMessage());
                }
            }
        }
    }

    /**
     * Interface for HTTP request callbacks
     */
    public interface RequestCallback {
        /**
         * Called when new response data is available
         *
         * @param bytes      The response data
         * @param len        The length of the data
         * @param currentLen The current length of the data
         * @param total      The total length of the data
         */
        default void updateResponseData(byte[] bytes, int len, int currentLen, int total) {
        }

        /**
         * Called when the request fails
         *
         * @param errorCode The error code
         * @param msg       The error message
         */
        default void requestFail(int errorCode, String msg) {
        }

        /**
         * Called when the request is complete
         */
        default void requestFinish() {
        }

        /**
         * Called when the request is complete
         */
        default void onHttpResponse(String responseTxt) {

        }
    }
}
