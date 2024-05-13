package io.agora.karaoke_view.internal.net;


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

import io.agora.karaoke_view.internal.config.Config;
import io.agora.karaoke_view.constants.Constants;
import io.agora.logging.LogManager;


public class HttpUrlRequest {
    private static final String TAG = Constants.TAG + "-HttpURLRequest";
    private RequestCallback mCallback;
    private volatile boolean mCancelled;

    public HttpUrlRequest() {
        mCancelled = false;
    }

    public void setCallback(RequestCallback callback) {
        this.mCallback = callback;
    }

    public void setCancelled(boolean cancelled) {
        this.mCancelled = cancelled;
    }

    public void requestPostUrl(String urlStr, Map<String, String> requestProperty, String writeData, boolean isStream) {
        LogManager.instance().debug(TAG, "http requestPostUrl urlStr:" + urlStr + ",requestProperty:" + requestProperty + ", writeData:" + writeData);
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
                // 获取服务器返回的头信息
                Map<String, List<String>> headers = conn.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    String name = entry.getKey();
                    for (String value : entry.getValue()) {
                        LogManager.instance().debug(TAG, "http response header:" + name + " : " + value);
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

    public void requestGetUrl(String urlStr, Map<String, String> headers) {
        LogManager.instance().debug(TAG, "http requestGetUrl urlStr:" + urlStr + ",headers:" + headers);
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
                // 获取服务器返回的头信息
                Map<String, List<String>> responseHeaders = conn.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                    String name = entry.getKey();
                    for (String value : entry.getValue()) {
                        LogManager.instance().debug(TAG, "http response header:" + name + " : " + value);
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
                    //just for server not support content-length
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
            LogManager.instance().debug(TAG, "http response " + e);
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

    public interface RequestCallback {
        default void updateResponseData(byte[] bytes, int len, int currentLen, int total) {
        }

        default void requestFail(int errorCode, String msg) {

        }

        default void requestFinish() {

        }

        default void onHttpResponse(String responseTxt) {

        }
    }
}
