package io.agora.karaoke_view.v11.downloader;

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

import io.agora.karaoke_view.v11.constants.Constants;
import io.agora.karaoke_view.v11.constants.DownloadError;
import io.agora.karaoke_view.v11.model.DownloadLyricModel;
import io.agora.karaoke_view.v11.net.HttpUrlRequest;
import io.agora.karaoke_view.v11.utils.Utils;
import io.agora.logging.LogManager;

public class LyricsFileDownloader {
    private static final String TAG = Constants.TAG + "-LyricsFileDownloader";
    private volatile static LyricsFileDownloader instance = null;
    private final Context mContext;
    private final ExecutorService mExecutorCacheService;
    private LyricsFileDownloaderCallback mLyricsFileDownloaderCallback;
    private int mMaxFileNum;
    private long mMaxFileAge;
    private int mRequestId;
    private final Map<String, DownloadLyricModel> mLyricsDownloaderMap;
    private static final int MAX_DOWNLOADER_NUM = 3;

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
     * set callback for download
     *
     * @param callback callback
     */
    public void setLyricsFileDownloaderCallback(LyricsFileDownloaderCallback callback) {
        mLyricsFileDownloaderCallback = callback;
    }

    /**
     * @param maxFileNum Maximum number of files
     */
    public void setMaxFileNum(int maxFileNum) {
        LogManager.instance().debug(TAG, "setMaxFileNum maxFileNum:" + maxFileNum);
        if (maxFileNum <= 0) {
            LogManager.instance().error(TAG, "setMaxFileNum maxFileNum is invalid");
            return;
        }
        mMaxFileNum = maxFileNum;
    }

    /**
     * @param maxFileAge Unit: seconds
     */
    public void setMaxFileAge(long maxFileAge) {
        LogManager.instance().debug(TAG, "setMaxFileAge maxFileAge:" + maxFileAge);
        if (maxFileAge <= 0) {
            LogManager.instance().error(TAG, "setMaxFileAge maxFileAge is invalid");
            return;
        }
        mMaxFileAge = maxFileAge;
    }

    /**
     * start a download
     *
     * @param url download url
     * @return requestId
     */
    public int download(final String url) {
        if (null == mContext) {
            LogManager.instance().error(TAG, "download context is null");
            return Constants.ERROR_GENERAL;
        }
        if (TextUtils.isEmpty(url)) {
            LogManager.instance().error(TAG, "download url is null");
            return Constants.ERROR_GENERAL;
        }

        synchronized (this) {
            LogManager.instance().debug(TAG, "download url:" + url);
            if (mLyricsDownloaderMap.containsKey(url)) {
                LogManager.instance().info(TAG, "download url is downloading");
                DownloadLyricModel model = mLyricsDownloaderMap.get(url);
                if (null != model) {
                    notifyLyricsFileDownloadCompleted(model.getRequestId(), null, DownloadError.REPEAT_DOWNLOADING);
                    return model.getRequestId();
                } else {
                    mLyricsDownloaderMap.remove(url);
                    LogManager.instance().error(TAG, "exception and remove downloading url:" + url);
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
                LogManager.instance().debug(TAG, "download " + file.getPath() + " exists");
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
                    LogManager.instance().debug(TAG, "download requestId:" + downloadRequestId + ",url:" + url + ",saveTo:" + file.getPath());
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

                            LogManager.instance().debug(TAG, "download progress requestId:" + downloadRequestId + ",url:" + url + ",currentLen:" + currentLen + ",total:" + total + ",progress:" + progress);
                            if (null != bytes) {
                                try {
                                    byte[] data = new byte[len];
                                    System.arraycopy(bytes, 0, data, 0, len);
                                    FileOutputStream fos = new FileOutputStream(file.getPath(), true);
                                    fos.write(data);
                                    fos.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    LogManager.instance().error(TAG, "download requestId:" + downloadRequestId + ",url:" + url + " write file fail:" + e);
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
                            LogManager.instance().error(TAG, "download fail requestId:" + downloadRequestId + ",url:" + url + " fail:" + errorCode + " " + msg);
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
                            LogManager.instance().debug(TAG, "download finish requestId:" + downloadRequestId + ",url:" + url + " finish");
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

    private synchronized void handleDownloadedFile(int requestId, File file) {
        LogManager.instance().debug(TAG, "handleDownloadedFile requestId:" + requestId + ",file:" + file.getPath());
        checkFileAge(new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_DOWNLOAD_DIR));
        if (file.exists()) {
            if (file.getName().toLowerCase().endsWith(Constants.FILE_EXTENSION_ZIP)) {
                LogManager.instance().debug(TAG, "handleDownloadedFile file is zip file");
                ByteArrayOutputStream byteArrayOutputStream = null;
                // buffer for read and write data to file
                byte[] buffer = new byte[1024];
                try (FileInputStream fis = new FileInputStream(file);
                     ZipInputStream zis = new ZipInputStream(fis)) {
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    //get first ZipEntry
                    ZipEntry entry = zis.getNextEntry();
                    String fileName = entry.getName();
                    LogManager.instance().debug(TAG, "handleDownloadedFile unzip file:" + fileName);
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
                        LogManager.instance().error(TAG, "handleDownloadedFile file is not support lyrics file " + fileName + " in zip file");
                        DownloadError error = DownloadError.UNZIP_FAIL;
                        error.setErrorCode(Constants.ERROR_UNZIP_ERROR);
                        notifyLyricsFileDownloadCompleted(requestId, null, error);
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    LogManager.instance().error(TAG, "handleDownloadedFile unzip exception:" + e);
                    notifyLyricsFileDownloadCompleted(requestId, null, DownloadError.UNZIP_FAIL);
                    return;
                }

                File folder = new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_TEMP_DIR);
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                String path = folder.getPath() + File.separator + file.getName().substring(0, file.getName().lastIndexOf(".")) + Constants.FILE_EXTENSION_XML;

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
                    LogManager.instance().error(TAG, "handleDownloadedFile write file exception:" + e);
                    notifyLyricsFileDownloadCompleted(requestId, null, DownloadError.UNZIP_FAIL);
                    return;
                }
                LogManager.instance().debug(TAG, "handleDownloadedFile unzip file success");
                handleLyricsFile(requestId, realFile, true);
            } else if (isSupportLyricsFile(file.getName())) {
                if (file.getName().toLowerCase().endsWith(Constants.FILE_EXTENSION_XML)) {
                    LogManager.instance().debug(TAG, "handleDownloadedFile file is xml file");
                    handleLyricsFile(requestId, file, false);
                } else if (file.getName().toLowerCase().endsWith(Constants.FILE_EXTENSION_LRC)) {
                    LogManager.instance().debug(TAG, "handleDownloadedFile file is lrc file");
                    handleLyricsFile(requestId, file, false);
                }
            } else {
                LogManager.instance().error(TAG, "handleDownloadedFile unknown file format and return directly");
                DownloadError error = DownloadError.UNZIP_FAIL;
                error.setErrorCode(Constants.ERROR_UNZIP_ERROR);
                notifyLyricsFileDownloadCompleted(requestId, null, error);
            }
        } else {
            LogManager.instance().error(TAG, "extractFromZipFileIfPossible file is not exists");

            notifyLyricsFileDownloadCompleted(requestId, null, DownloadError.HTTP_DOWNLOAD_ERROR);
        }
    }

    private void handleLyricsFile(int requestId, File lyricFile, boolean deleteLyricsFile) {
        notifyLyricsFileDownloadCompleted(requestId, lyricFile, null);

        if (deleteLyricsFile) {
            if (lyricFile.exists()) {
                LogManager.instance().debug(TAG, "handleLyricsFile delete file:" + lyricFile.getPath());
                lyricFile.delete();
            }
        }
        LogManager.instance().debug(TAG, "handleLyricsFile success");
    }

    private synchronized void checkMaxFileNum() {
        File folder = new File(mContext.getExternalCacheDir(), Constants.LYRICS_FILE_DOWNLOAD_DIR);
        File[] files = folder.listFiles();
        if (null == files) {
            LogManager.instance().info(TAG, "checkMaxFileNum files is empty");
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
            LogManager.instance().debug(TAG, "checkMaxFileNum fileList size:" + fileList.size() + " is less than maxFileNum:" + mMaxFileNum);
            return;
        }
        LogManager.instance().debug(TAG, "checkMaxFileNum fileList :" + fileList);
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
            LogManager.instance().debug(TAG, "maybeDeleteFile delete file:" + file.getPath());
            file.delete();
        }

        return canDelete;
    }

    private synchronized void checkFileAge(File folder) {
        File[] files = folder.listFiles();
        if (null == files) {
            return;
        }
        LogManager.instance().debug(TAG, "checkFileAge files :" + Arrays.toString(files));
        long now = System.currentTimeMillis();
        for (File file : files) {
            if (file.isFile() && (now - file.lastModified() > mMaxFileAge * 1000)) {
                LogManager.instance().debug(TAG, "checkFileAge delete file:" + file.getPath());
                file.delete();
            }
        }
    }

    private boolean isSupportLyricsFile(String fileName) {
        return fileName.toLowerCase().endsWith("." + Constants.FILE_EXTENSION_XML) || fileName.toLowerCase().endsWith("." + Constants.FILE_EXTENSION_LRC);
    }

    private synchronized void notifyLyricsFileDownloadCompleted(int requestId, File lyricFile, DownloadError error) {
        checkMaxFileNum();
        if (null != mLyricsFileDownloaderCallback) {
            if (null != lyricFile) {
                LogManager.instance().debug(TAG, "notifyLyricsFileDownloadCompleted requestId:" + requestId + ",lyricFile:" + lyricFile.getPath() + ",error:" + error);
                mLyricsFileDownloaderCallback.onLyricsFileDownloadCompleted(requestId, Utils.readFileToByteArray(lyricFile.getPath()), null);
            } else {
                LogManager.instance().debug(TAG, "notifyLyricsFileDownloadCompleted requestId:" + requestId + ",lyricFile is null,error:" + error);
                mLyricsFileDownloaderCallback.onLyricsFileDownloadCompleted(requestId, null, error);
            }
        } else {
            LogManager.instance().debug(TAG, "notifyLyricsFileDownloadCompleted mLyricsFileDownloaderCallback is null");
        }
    }

    /**
     * cancel a downloading task
     *
     * @param requestId requestId for download
     */
    public void cancelDownload(int requestId) {
        if (null == mContext) {
            LogManager.instance().error(TAG, "cancelDownload context is null");
            return;
        }
        LogManager.instance().debug(TAG, "cancelDownload requestId:" + requestId);
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
     * clean all files in local
     */
    public void cleanAll() {
        if (null == mContext) {
            LogManager.instance().error(TAG, "cleanAll context is null");
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
        LogManager.instance().debug(TAG, "cleanAll isCleanAllSuccess:" + isCleanAllSuccess);
    }
}
