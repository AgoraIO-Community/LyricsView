package io.agora.karaoke_view_ex.downloader;

import android.content.Context;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.constants.DownloadError;
import io.agora.karaoke_view_ex.internal.model.DownloadLyricModel;
import io.agora.karaoke_view_ex.internal.net.HttpUrlRequest;
import io.agora.karaoke_view_ex.internal.utils.LogUtils;
import io.agora.karaoke_view_ex.utils.Utils;

/**
 * Manages the downloading and caching of lyrics files.
 * Handles file downloads, unzipping, and cache management with configurable limits.
 */
public class LyricsFileDownloader {
    private static final String TAG = Constants.TAG + "-LyricsFileDownloader";

    /**
     * Singleton instance of the downloader
     */
    private volatile static LyricsFileDownloader instance = null;

    /**
     * Application context
     */
    private final Context mContext;

    /**
     * Thread pool for handling download tasks
     */
    private final ExecutorService mExecutorCacheService;

    /**
     * Callback for download events
     */
    private LyricsFileDownloaderCallback mLyricsFileDownloaderCallback;

    /**
     * Maximum number of files to keep in cache
     */
    private int mMaxFileNum;

    /**
     * Maximum age of cached files in seconds
     */
    private long mMaxFileAge;

    /**
     * Current request ID counter
     */
    private int mRequestId;

    /**
     * Map of active downloads
     */
    private final Map<String, DownloadLyricModel> mLyricsDownloaderMap;

    /**
     * Maximum number of concurrent downloads
     */
    private static final int MAX_DOWNLOADER_NUM = 3;

    /**
     * Private constructor for singleton pattern
     *
     * @param context Application context
     */
    private LyricsFileDownloader(Context context) {
        mContext = context;
        mExecutorCacheService = new ThreadPoolExecutor(MAX_DOWNLOADER_NUM, MAX_DOWNLOADER_NUM,
                0, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        mMaxFileNum = 50;
        mMaxFileAge = 8 * 60 * 60;
        mRequestId = -1;
        mLyricsDownloaderMap = new ConcurrentHashMap<>(MAX_DOWNLOADER_NUM);
    }

    /**
     * Get singleton instance of LyricsFileDownloader
     *
     * @param context Application context
     * @return LyricsFileDownloader instance
     */
    public static LyricsFileDownloader getInstance(Context context) {
        if (instance == null) {
            synchronized (LyricsFileDownloader.class) {
                if (instance == null) {
                    instance = new LyricsFileDownloader(context);
                }
            }
        }
        return instance;
    }

    /**
     * Set callback for download events
     *
     * @param callback Callback implementation
     */
    public void setLyricsFileDownloaderCallback(LyricsFileDownloaderCallback callback) {
        mLyricsFileDownloaderCallback = callback;
    }

    /**
     * Set maximum number of files to keep in cache
     *
     * @param maxFileNum Maximum number of files
     */
    public void setMaxFileNum(int maxFileNum) {
        LogUtils.d("setMaxFileNum maxFileNum:" + maxFileNum);
        if (maxFileNum <= 0) {
            LogUtils.e("setMaxFileNum maxFileNum is invalid");
            return;
        }
        mMaxFileNum = maxFileNum;
    }

    /**
     * Set maximum age for cached files
     *
     * @param maxFileAge Maximum file age in seconds
     */
    public void setMaxFileAge(long maxFileAge) {
        LogUtils.d("setMaxFileAge maxFileAge:" + maxFileAge);
        if (maxFileAge <= 0) {
            LogUtils.e("setMaxFileAge maxFileAge is invalid");
            return;
        }
        mMaxFileAge = maxFileAge;
    }

    /**
     * Start downloading a lyrics file
     *
     * @param url URL of the lyrics file to download
     * @return Request ID for the download task
     */
    public int download(final String url) {
        if (null == mContext) {
            LogUtils.e("download context is null");
            return Constants.ERROR_GENERAL;
        }
        if (TextUtils.isEmpty(url)) {
            LogUtils.e("download url is null");
            return Constants.ERROR_GENERAL;
        }

        synchronized (this) {
            LogUtils.d("download url:" + url);
            if (mLyricsDownloaderMap.containsKey(url)) {
                LogUtils.i("download url is downloading");
                DownloadLyricModel model = mLyricsDownloaderMap.get(url);
                if (null != model) {
                    notifyLyricsFileDownloadCompleted(model.getRequestId(), null, DownloadError.REPEAT_DOWNLOADING);
                    return model.getRequestId();
                } else {
                    mLyricsDownloaderMap.remove(url);
                    LogUtils.e("exception and remove downloading url:" + url);
                }
            }

            File folder = new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_DOWNLOAD_DIR);
            if (!folder.exists()) {
                folder.mkdir();
            }

            if (mRequestId + 1 == Integer.MAX_VALUE) {
                mRequestId = -1;
            }
            mRequestId++;

            checkFileAge(folder);

            String urlFileName = url.substring(url.lastIndexOf("/") + 1);
            if (TextUtils.isEmpty(urlFileName)) {
                urlFileName = System.currentTimeMillis() + "." + Constants.FILE_EXTENSION_ZIP;
            }
            final File file = new File(folder, urlFileName);
            if (file.exists()) {
                LogUtils.d("download " + file.getPath() + " exists");
                handleDownloadedFile(mRequestId, file);
                return mRequestId;
            }

            final DownloadLyricModel model = new DownloadLyricModel();
            model.setUrl(url);
            model.setFilePath(file.getPath());
            model.setRequestId(mRequestId);
            final HttpUrlRequest request = new HttpUrlRequest();
            model.setHttpUrlRequest(request);
            mLyricsDownloaderMap.put(url, model);

            final int downloadRequestId = mRequestId;
            mExecutorCacheService.execute(new Runnable() {
                @Override
                public void run() {
                    LogUtils.d("download requestId:" + downloadRequestId + ",url:" + url + ",saveTo:" + file.getPath());
                    request.setCallback(new HttpUrlRequest.RequestCallback() {
                        @Override
                        public void updateResponseData(byte[] bytes, int len, int currentLen, int total) {
                            if (!mLyricsDownloaderMap.containsKey(url)) {
                                return;
                            }
                            float progress = 0;
                            if (-1 != total) {
                                progress = ((int) (currentLen * 100 / total)) / 100.0f;
                            }

                            LogUtils.d("download progress requestId:" + downloadRequestId + ",url:" + url + ",currentLen:" + currentLen + ",total:" + total + ",progress:" + progress);
                            if (null != bytes) {
                                try {
                                    byte[] data = new byte[len];
                                    System.arraycopy(bytes, 0, data, 0, len);
                                    FileOutputStream fos = new FileOutputStream(file.getPath(), true);
                                    fos.write(data);
                                    fos.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    LogUtils.e("download requestId:" + downloadRequestId + ",url:" + url + " write file fail:" + e);
                                }
                            }
                            if (null != mLyricsFileDownloaderCallback) {
                                mLyricsFileDownloaderCallback.onLyricsFileDownloadProgress(downloadRequestId, progress);
                            }
                        }

                        @Override
                        public void requestFail(int errorCode, String msg) {
                            if (!mLyricsDownloaderMap.containsKey(url)) {
                                return;
                            }
                            LogUtils.e("download fail requestId:" + downloadRequestId + ",url:" + url + " fail:" + errorCode + " " + msg);
                            cancelDownload(downloadRequestId);
                            if (null != mLyricsFileDownloaderCallback) {
                                DownloadError downloadError;
                                if (errorCode < 0) {
                                    downloadError = DownloadError.HTTP_DOWNLOAD_ERROR;
                                    downloadError.setMessage(msg);
                                    downloadError.setErrorCode(errorCode);
                                    notifyLyricsFileDownloadCompleted(downloadRequestId, null, downloadError);
                                } else {
                                    downloadError = DownloadError.HTTP_DOWNLOAD_ERROR_LOGIC;
                                    downloadError.setMessage(msg);
                                    downloadError.setErrorCode(errorCode);
                                    notifyLyricsFileDownloadCompleted(downloadRequestId, null, downloadError);
                                }
                            }
                        }

                        @Override
                        public void requestFinish() {
                            if (!mLyricsDownloaderMap.containsKey(url)) {
                                return;
                            }
                            LogUtils.d("download finish requestId:" + downloadRequestId + ",url:" + url + " finish");
                            handleDownloadedFile(downloadRequestId, file);
                            mLyricsDownloaderMap.remove(url);
                        }
                    });
                    request.requestGetUrl(url, null);
                }
            });
            return mRequestId;
        }
    }

    /**
     * Handle a downloaded file, including unzipping if necessary
     *
     * @param requestId Request ID of the download
     * @param file      Downloaded file
     */
    private synchronized void handleDownloadedFile(int requestId, File file) {
        LogUtils.d("handleDownloadedFile requestId:" + requestId + ",file:" + file.getPath());
        checkFileAge(new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_DOWNLOAD_DIR));
        if (file.exists()) {
            if (file.getName().toLowerCase().endsWith(Constants.FILE_EXTENSION_ZIP)) {
                LogUtils.d("handleDownloadedFile file is zip file");
                ByteArrayOutputStream byteArrayOutputStream = null;
                // buffer for read and write data to file
                byte[] buffer = new byte[1024];
                try (FileInputStream fis = new FileInputStream(file);
                     ZipInputStream zis = new ZipInputStream(fis)) {
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    //get first ZipEntry
                    ZipEntry entry = zis.getNextEntry();
                    String fileName = entry.getName();
                    LogUtils.d("handleDownloadedFile unzip file:" + fileName);
                    if (isSupportLyricsFile(fileName)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            byteArrayOutputStream.write(buffer, 0, len);
                        }
                        byteArrayOutputStream.flush();
                        // close this ZipEntry
                        zis.closeEntry();
                        byteArrayOutputStream.close();
                        // close last ZipEntry
                    } else {
                        zis.closeEntry();
                        LogUtils.e("handleDownloadedFile file is not support lyrics file " + fileName + " in zip file");
                        DownloadError error = DownloadError.UNZIP_FAIL;
                        error.setErrorCode(Constants.ERROR_UNZIP_ERROR);
                        notifyLyricsFileDownloadCompleted(requestId, null, error);
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    LogUtils.e("handleDownloadedFile unzip exception:" + e);
                    notifyLyricsFileDownloadCompleted(requestId, null, DownloadError.UNZIP_FAIL);
                    return;
                }

                File folder = new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_TEMP_DIR);
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                String path = folder.getPath() + File.separator + file.getName().substring(0, file.getName().lastIndexOf(".")) + "." + Constants.FILE_EXTENSION_XML;

                File realFile = new File(path);

                try {
                    if (realFile.exists()) {
                        realFile.delete();
                    }
                    FileOutputStream fos = new FileOutputStream(realFile);
                    fos.write(byteArrayOutputStream.toByteArray());
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.e("handleDownloadedFile write file exception:" + e);
                    notifyLyricsFileDownloadCompleted(requestId, null, DownloadError.UNZIP_FAIL);
                    return;
                }
                LogUtils.d("handleDownloadedFile unzip file success");
                handleLyricsFile(requestId, realFile, true);
            } else if (isSupportLyricsFile(file.getName())) {
                if (file.getName().toLowerCase().endsWith(Constants.FILE_EXTENSION_XML)) {
                    LogUtils.d("handleDownloadedFile file is xml file");
                    handleLyricsFile(requestId, file, false);
                } else if (file.getName().toLowerCase().endsWith(Constants.FILE_EXTENSION_LRC)) {
                    LogUtils.d("handleDownloadedFile file is lrc file");
                    handleLyricsFile(requestId, file, false);
                } else if (file.getName().toLowerCase().endsWith(Constants.FILE_EXTENSION_KRC)) {
                    LogUtils.d("handleDownloadedFile file is krc file");
                    handleLyricsFile(requestId, file, false);
                }
            } else {
                LogUtils.e("handleDownloadedFile unknown file format and return directly");
                DownloadError error = DownloadError.UNZIP_FAIL;
                error.setErrorCode(Constants.ERROR_UNZIP_ERROR);
                notifyLyricsFileDownloadCompleted(requestId, null, error);
            }
        } else {
            LogUtils.e("extractFromZipFileIfPossible file is not exists");
            notifyLyricsFileDownloadCompleted(requestId, null, DownloadError.HTTP_DOWNLOAD_ERROR);
        }
    }

    /**
     * Process a lyrics file and notify completion
     *
     * @param requestId        Request ID of the download
     * @param lyricFile        The lyrics file to process
     * @param deleteLyricsFile Whether to delete the file after processing
     */
    private void handleLyricsFile(int requestId, File lyricFile, boolean deleteLyricsFile) {
        notifyLyricsFileDownloadCompleted(requestId, lyricFile, null);

        if (deleteLyricsFile) {
            if (lyricFile.exists()) {
                LogUtils.d("handleLyricsFile delete file:" + lyricFile.getPath());
                lyricFile.delete();
            }
        }
        LogUtils.d("handleLyricsFile success");
    }

    /**
     * Check and enforce maximum file number limit
     */
    private synchronized void checkMaxFileNum() {
        File folder = new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_DOWNLOAD_DIR);
        File[] files = folder.listFiles();
        if (null == files) {
            LogUtils.i("checkMaxFileNum files is empty");
            return;
        }
        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        Iterator<File> iterator = fileList.iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (!file.isFile()) {
                iterator.remove();
            }
        }
        if (fileList.size() <= mMaxFileNum) {
            LogUtils.d("checkMaxFileNum fileList size:" + fileList.size() + " is less than maxFileNum:" + mMaxFileNum);
            return;
        }
        LogUtils.d("checkMaxFileNum fileList :" + fileList);
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return Long.compare(file1.lastModified(), file2.lastModified());
            }
        });

        for (int i = 0; i < fileList.size() - mMaxFileNum; i++) {
            do {
                if (maybeDeleteFile(fileList.get(i))) {
                    break;
                } else {
                    i++;
                }
            } while (i < files.length - 1);
        }
    }

    /**
     * Attempt to delete a file if it's not being downloaded
     *
     * @param file File to potentially delete
     * @return Whether the file was deleted
     */
    private boolean maybeDeleteFile(File file) {
        if (null == file || !file.exists()) {
            return true;
        }
        boolean canDelete = true;
        for (DownloadLyricModel model : mLyricsDownloaderMap.values()) {
            if (null != model && model.getFilePath().equals(file.getPath())) {
                canDelete = false;
                break;
            }
        }
        if (canDelete) {
            LogUtils.d("maybeDeleteFile delete file:" + file.getPath());
            file.delete();
        }

        return canDelete;
    }

    /**
     * Check and delete files older than maximum age
     *
     * @param folder Folder to check
     */
    private synchronized void checkFileAge(File folder) {
        File[] files = folder.listFiles();
        if (null == files) {
            return;
        }
        LogUtils.d("checkFileAge files :" + Arrays.toString(files));
        long now = System.currentTimeMillis();
        for (File file : files) {
            if (file.isFile() && (now - file.lastModified() > mMaxFileAge * 1000)) {
                LogUtils.d("checkFileAge delete file:" + file.getPath());
                file.delete();
            }
        }
    }

    /**
     * Check if file is a supported lyrics format
     *
     * @param fileName Name of file to check
     * @return Whether the file format is supported
     */
    private boolean isSupportLyricsFile(String fileName) {
        return fileName.toLowerCase().endsWith("." + Constants.FILE_EXTENSION_XML) ||
                fileName.toLowerCase().endsWith("." + Constants.FILE_EXTENSION_LRC) ||
                fileName.toLowerCase().endsWith("." + Constants.FILE_EXTENSION_KRC);
    }

    /**
     * Notify callback of download completion
     *
     * @param requestId Request ID of the download
     * @param lyricFile The downloaded lyrics file
     * @param error     Error that occurred, if any
     */
    private synchronized void notifyLyricsFileDownloadCompleted(int requestId, File lyricFile, DownloadError error) {
        checkMaxFileNum();
        if (null != mLyricsFileDownloaderCallback) {
            if (null != lyricFile) {
                LogUtils.d("notifyLyricsFileDownloadCompleted requestId:" + requestId + ",lyricFile:" + lyricFile.getPath() + ",error:" + error);
                mLyricsFileDownloaderCallback.onLyricsFileDownloadCompleted(requestId, Utils.readFileToByteArray(lyricFile.getPath()), null);
            } else {
                LogUtils.d("notifyLyricsFileDownloadCompleted requestId:" + requestId + ",lyricFile is null,error:" + error);
                mLyricsFileDownloaderCallback.onLyricsFileDownloadCompleted(requestId, null, error);
            }
        } else {
            LogUtils.d("notifyLyricsFileDownloadCompleted mLyricsFileDownloaderCallback is null");
        }
    }

    /**
     * Cancel an ongoing download
     *
     * @param requestId Request ID of the download to cancel
     */
    public void cancelDownload(int requestId) {
        if (null == mContext) {
            LogUtils.e("cancelDownload context is null");
            return;
        }
        LogUtils.d("cancelDownload requestId:" + requestId);
        Collection<DownloadLyricModel> values = mLyricsDownloaderMap.values();
        String url = "";
        for (DownloadLyricModel model : values) {
            if (null != model && model.getRequestId() == requestId) {
                model.getHttpUrlRequest().setCancelled(true);
                url = model.getUrl();
                File tempFile = new File(model.getFilePath());
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                break;
            }
        }
        if (!TextUtils.isEmpty(url)) {
            mLyricsDownloaderMap.remove(url);
        }
    }

    /**
     * Clean all downloaded files from cache
     */
    public void cleanAll() {
        if (null == mContext) {
            LogUtils.e("cleanAll context is null");
            return;
        }
        File folder = new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_DOWNLOAD_DIR);
        boolean isCleanAllSuccess = true;
        if (folder.exists()) {
            File[] files = folder.listFiles();
            if (null != files) {
                for (File file : files) {
                    if (file.isFile()) {
                        isCleanAllSuccess &= file.delete();
                    }
                }
            }
        }
        LogUtils.d("cleanAll isCleanAllSuccess:" + isCleanAllSuccess);
    }
}
