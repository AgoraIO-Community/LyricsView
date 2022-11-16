package io.agora.examples.lyrics_view;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.AndroidRuntimeException;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.agora.examples.utils.ResourceHelper;
import io.agora.lyrics_view.DownloadManager;
import io.agora.lyrics_view.LrcLoadUtils;
import io.agora.lyrics_view.LrcView;
import io.agora.lyrics_view.bean.LrcData;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, LrcView.OnLyricsSeekListener {

    private LrcView lrcView;
    private int mCurrentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.clear_cache).setOnClickListener(this);
        lrcView = findViewById(R.id.lrc_view);
        lrcView.setSeekListener(this);

        loadLrc(LyricsResourcePool.LRC_SAMPLE_1);
    }

    private void loadLrc(String lrcSample) {
        lrcView.reset();
        if (lrcSample.startsWith("https://") || lrcSample.startsWith("http://")) {
            DownloadManager.getInstance().download(this, lrcSample, file -> {
                file = extractFromZipFileIfPossible(file);
                LrcData lrcData = LrcLoadUtils.parse(file);
                lrcView.setLrcData(lrcData);
            }, Throwable::printStackTrace);
        } else {
            File file = ResourceHelper.copyAssetsToCreateNewFile(getApplicationContext(), lrcSample);
            file = extractFromZipFileIfPossible(file);
            LrcData lrcData = LrcLoadUtils.parse(file);
            lrcView.setLrcData(lrcData);
        }
    }

    private static File extractFromZipFileIfPossible(File file) {
        if (!(file.isFile() && file.getName().endsWith(".zip"))) {
            return file;
        }

        if (file.length() >= 1 * 1024 * 1024) { // Too large file, we do not support
            throw new AndroidRuntimeException("Too large lyrics file, we do not support so far " + file.length());
        }

        ByteArrayOutputStream byteArrayOutputStream = null;
        FileInputStream fis;
        // buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(file);
            ZipInputStream zis = new ZipInputStream(fis);
            byteArrayOutputStream = new ByteArrayOutputStream();
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                byteArrayOutputStream.flush();
                // close this ZipEntry
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
            byteArrayOutputStream.close();
            // close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        file.delete();
        String path = file.getAbsolutePath().replaceAll(".zip", ".xml");

        File realFile = new File(path);
        try {
            FileOutputStream fos = new FileOutputStream(realFile);
            fos.write(byteArrayOutputStream.toByteArray());
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return realFile;
    }

    @Override
    public void onClick(View view) {
        DownloadManager.getInstance().clearCache(this);

        loadLrc(LyricsResourcePool.asList().get(mCurrentIndex));

        mCurrentIndex++;
        if (mCurrentIndex >= LyricsResourcePool.asList().size()) {
            mCurrentIndex = 0;
        }
    }

    @Override
    public void onProgressChanged(long time) {
        lrcView.updateTime(time + 1000);
    }

    @Override
    public void onStartTrackingTouch() {

    }

    @Override
    public void onStopTrackingTouch() {

    }
}