package io.agora.examples.karaoke_view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.agora.examples.karaoke_view.databinding.ActivityMainBinding;
import io.agora.examples.net.NetworkClient;
import io.agora.examples.utils.KeyCenter;
import io.agora.examples.utils.MccExManager;
import io.agora.examples.utils.MusicContentCenterManager;
import io.agora.examples.utils.RtcManager;
import io.agora.examples.utils.ToastUtils;
import io.agora.examples.utils.Utils;
import io.agora.karaoke_view.KaraokeEvent;
import io.agora.karaoke_view.KaraokeView;
import io.agora.karaoke_view.constants.Constants;
import io.agora.karaoke_view.constants.DownloadError;
import io.agora.karaoke_view.downloader.LyricsFileDownloader;
import io.agora.karaoke_view.downloader.LyricsFileDownloaderCallback;
import io.agora.karaoke_view.model.LyricModel;
import io.agora.mccex.constants.MccExState;
import io.agora.mccex.constants.MccExStateReason;
import io.agora.mccex.model.LineScoreData;
import io.agora.mccex.model.RawScoreData;
import io.agora.rtc2.IRtcEngineEventHandler;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        MusicContentCenterManager.MccCallback, MccExManager.MccExCallback {
    private static final String TAG = Constants.TAG + "-Main";
    private ActivityMainBinding binding;
    private KaraokeView mKaraokeView;
    private int mCurrentSongCodeIndex = 0;
    private LyricModel mLyricsModel;
    private ActivityResultLauncher mLauncher;

    private Handler mHandler;
    private MusicContentCenterManager mMusicContentCenterManager;
    private RtcManager mRtcManager;
    private MccExManager mMccExManager;
    private final boolean mMccExService = true;

    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    private long mLyricsCurrentProgress = 0;
    private ScheduledFuture mFuture;

    private Player_State mState = Player_State.Uninitialized;

    private enum Player_State {
        Uninitialized(-1),
        Idle(0),
        Playing(1),
        Pause(2);

        private int state;

        private Player_State(int def) {
            this.state = def;
        }

        public int getState() {
            return state;
        }
    }

    private static final int TAG_PERMISSION_REQUEST_CODE = 1000;
    private static final String[] PERMISSION = new String[]{
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (mMccExService) {
            mMccExManager = MccExManager.INSTANCE;
            initMccEx();
        } else {
            mMusicContentCenterManager = new MusicContentCenterManager(this, this);
            mMusicContentCenterManager.init();
        }
        mHandler = new Handler(this.getMainLooper());

        binding.switchToNext.setOnClickListener(this);
        binding.play.setOnClickListener(this);
        binding.pause.setOnClickListener(this);
        binding.skipTheIntro.setOnClickListener(this);
        binding.settings.setOnClickListener(this);

        mKaraokeView = new KaraokeView(binding.enableLyrics.isChecked() ? binding.lyricsView : null, binding.enableScoring.isChecked() ? binding.scoringView : null);

        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
                mLyricsCurrentProgress = position;
                updateCallback("Dragging, new progress " + position);
                if (mMccExService) {
                    mMccExManager.seek(position);
                    mMccExManager.updateMusicPosition(position);
                } else {
                    mMusicContentCenterManager.seek(position);
                    mMusicContentCenterManager.updateMusicPosition(position);
                }
            }
        });

        loadPreferences();

        binding.enableLyrics.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mLyricsCurrentProgress <= 0) {
                    return;
                }

                mKaraokeView.attachUi(isChecked ? binding.lyricsView : null, binding.enableScoring.isChecked() ? binding.scoringView : null);
            }
        });

        binding.enableScoring.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mLyricsCurrentProgress <= 0) {
                    return;
                }

                mKaraokeView.attachUi(binding.enableLyrics.isChecked() ? binding.lyricsView : null, isChecked ? binding.scoringView : null);
            }
        });

        mLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if (result.getResultCode() == RESULT_OK) {
                    loadPreferences();
                }
            }
        });

        LyricsFileDownloader.getInstance(this).setMaxFileNum(3);
        LyricsFileDownloader.getInstance(this).setMaxFileAge(60);
        LyricsFileDownloader.getInstance(this).setLyricsFileDownloaderCallback(new LyricsFileDownloaderCallback() {
            @Override
            public void onLyricsFileDownloadProgress(int requestId, float progress) {
                Log.d(TAG, "onLyricsFileDownloadProgress requestId:" + requestId + " progress:" + progress);
            }

            @Override
            public void onLyricsFileDownloadCompleted(int requestId, byte[] fileData, DownloadError error) {
                Log.d(TAG, "onLyricsFileDownloadCompleted requestId:" + requestId + " error:" + error);
                if (null == error && null != fileData) {
                    Log.d(TAG, "onLyricsFileDownloadCompleted fileData:" + fileData.length);
                    mLyricsModel = KaraokeView.parseLyricData(fileData, null);

                    if (mLyricsModel != null) {
                        mKaraokeView.setLyricData(mLyricsModel);
                    }
                    playMusic();
                    updateLyricsDescription();
                } else {
                    mLyricsModel = null;

                    String description = LyricsResourcePool.asList().get(mCurrentSongCodeIndex).description; // For Testing
                    if (description != null && description.contains("SHOW_NO_LYRICS_TIPS")) {
                        mKaraokeView.setLyricData(null); // Call this will trigger no/invalid lyrics ui
                    }
                    playMusic();
                    updateLyricsDescription();
                }
            }
        });
    }

    private void initMccEx() {
        new Thread(new Runnable() {
            @Override
            public void run() {


                NetworkClient.INSTANCE.sendHttpsRequest(BuildConfig.YSD_TOKEN_HOST + KeyCenter.getUserUid(), new HashMap<>(0), "", false, new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.d(TAG, "initMccEx onFailure: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String responseData = response.body().string();
                        Log.d(TAG, "initMccEx onResponse: " + responseData);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    JSONObject responseJson = new JSONObject(responseData);
                                    JSONObject dataJson = responseJson.getJSONObject("data");
                                    String token = dataJson.getString("token");
                                    String userId = dataJson.getString("yinsuda_uid");

                                    mRtcManager = RtcManager.INSTANCE;
                                    mRtcManager.initRtcEngine(MainActivity.this, new RtcManager.RtcCallback() {
                                        @Override
                                        public void onAudioVolumeIndication(@Nullable IRtcEngineEventHandler.AudioVolumeInfo[] speakers, int totalVolume) {
                                        }

                                        @Override
                                        public void onUnMuteSuccess() {
                                        }

                                        @Override
                                        public void onMuteSuccess() {
                                        }

                                        @Override
                                        public void onJoinChannelSuccess(@NonNull String channel, int uid, int elapsed) {
                                        }

                                        @Override
                                        public void onLeaveChannel(@NonNull IRtcEngineEventHandler.RtcStats stats) {

                                        }
                                    });
                                    mMccExManager.setTokenAndUserId(token, userId);
                                    mMccExManager.initMccExService(RtcManager.getRtcEngine(), mRtcManager, MainActivity.this, MainActivity.this);

                                } catch (Exception e) {
                                    Log.e(TAG, "initMccEx onResponse: " + e.getMessage());
                                    ToastUtils.toastLong(MainActivity.this, "initMccEx onResponse: " + e.getMessage());
                                }
                            }
                        });
                    }
                });
            }
        }).start();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!EasyPermissions.hasPermissions(this, PERMISSION)) {
            EasyPermissions.requestPermissions(this, getString(R.string.error_permission),
                    TAG_PERMISSION_REQUEST_CODE, PERMISSION);
            return;
        }
    }

    private void loadTheLyrics(String lrcUri, String pitchUri) {
        Log.i(TAG, "loadTheLyrics " + lrcUri + " " + pitchUri);
        mKaraokeView.reset();
        mLyricsModel = null;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tvDescription = findViewById(R.id.lyrics_description);
                tvDescription.setText("Try to load " + lrcUri);
            }
        });
        if (TextUtils.isEmpty(lrcUri)) {
            mLyricsModel = null;

            // Call this will trigger no/invalid lyrics ui
            mKaraokeView.setLyricData(null);
            playMusic();
            updateLyricsDescription();
        } else if (lrcUri.startsWith("https://") || lrcUri.startsWith("http://")) {
            LyricsFileDownloader.getInstance(this).download(lrcUri);
        } else {
            File lrc;
            File pitch;
            if (mMccExService) {
                lrc = new File(lrcUri);
                pitch = new File(pitchUri);
                mLyricsModel = KaraokeView.parseLyricData(lrc, pitch);

                mKaraokeView.setLyricData(mLyricsModel);
            } else {
                lrc = Utils.copyAssetsToCreateNewFile(getApplicationContext(), lrcUri);
                pitch = Utils.copyAssetsToCreateNewFile(getApplicationContext(), pitchUri);
                lrc = extractFromZipFileIfPossible(lrc);
                mLyricsModel = KaraokeView.parseLyricData(lrc, pitch);

                if (mLyricsModel != null) {
                    mKaraokeView.setLyricData(mLyricsModel);
                }
            }
            playMusic();
            updateLyricsDescription();

        }
    }

    private void updateLyricsDescription() {
        String lyricsSummary = mLyricsModel != null ? (mLyricsModel.name + ": " + mLyricsModel.singer + " " + mLyricsModel.preludeEndPosition + " " + mLyricsModel.lines.size() + " " + mLyricsModel.duration) : "Invalid lyrics";
        Log.d(TAG, "lyricsSummary: " + lyricsSummary);
        final String finalDescription = lyricsSummary + "\n";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tvDescription = findViewById(R.id.lyrics_description);
                tvDescription.setText(finalDescription);
            }
        });
    }

    private void playMusic() {
        Log.i(TAG, "playMusic");
        if (mMccExService) {
            mMccExManager.startScore(LyricsResourcePool.asMusicListEx().get(mCurrentSongCodeIndex).songId, "");
        } else {
            mMusicContentCenterManager.openMusic(LyricsResourcePool.asMusicList().get(mCurrentSongCodeIndex).songCode);
        }
    }

    private void loadPreferences() {
        Log.i(TAG, "loadPreferences");
        SharedPreferences prefs = getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE);
        //int scoringLevel = prefs.getInt(getString(R.string.prefs_key_scoring_level), mKaraokeView.getScoringLevel());
        //mKaraokeView.setScoringLevel(scoringLevel);
        //int scoringOffset = prefs.getInt(getString(R.string.prefs_key_scoring_compensation_offset), mKaraokeView.getScoringCompensationOffset());
        //mKaraokeView.setScoringCompensationOffset(scoringOffset);

        boolean indicatorOn = prefs.getBoolean(getString(R.string.prefs_key_start_of_verse_indicator_switch), true);
        binding.lyricsView.enablePreludeEndPositionIndicator(indicatorOn);
        String indicatorColor = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_color), "Default");
        binding.lyricsView.setPreludeEndPositionIndicatorColor(colorInStringToDex(indicatorColor));
        String indicatorRadius = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_radius), "6dp");
        binding.lyricsView.setPreludeEndPositionIndicatorRadius(dp2pix(Float.parseFloat(indicatorRadius.replace("dp", ""))));
        int indicatorPaddingTop = prefs.getInt(getString(R.string.prefs_key_start_of_verse_indicator_padding_top), 6);
        binding.lyricsView.setPreludeEndPositionIndicatorPaddingTop(dp2pix(indicatorPaddingTop));

        String defaultTextColor = prefs.getString(getString(R.string.prefs_key_normal_line_text_color), "Default");
        binding.lyricsView.setPreviousLineTextColor(colorInStringToDex(defaultTextColor));
        binding.lyricsView.setUpcomingLineTextColor(colorInStringToDex(defaultTextColor));

        int defaultTextSize = prefs.getInt(getString(R.string.prefs_key_normal_line_text_size), 13);
        binding.lyricsView.setTextSize(sp2pix(defaultTextSize));

        String currentTextColor = prefs.getString(getString(R.string.prefs_key_current_line_text_color), "Yellow");
        binding.lyricsView.setCurrentLineTextColor(colorInStringToDex(currentTextColor));

        String highlightedTextColor = prefs.getString(getString(R.string.prefs_key_current_line_highlighted_text_color), "Red");
        binding.lyricsView.setCurrentLineHighlightedTextColor(colorInStringToDex(highlightedTextColor));

        int currentTextSize = prefs.getInt(getString(R.string.prefs_key_current_line_text_size), 24);
        binding.lyricsView.setCurrentLineTextSize(sp2pix(currentTextSize));

        String lineSpacing = prefs.getString(getString(R.string.prefs_key_line_spacing), "6dp");
        binding.lyricsView.setLineSpacing(dp2pix(Float.parseFloat(lineSpacing.replace("dp", ""))));

        boolean lyricsDraggingOn = prefs.getBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), true);
        binding.lyricsView.enableDragging(lyricsDraggingOn);

        String labelWhenNoLyrics = prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text), getString(R.string.no_lyrics_label));
        binding.lyricsView.setLabelShownWhenNoLyrics(labelWhenNoLyrics);

        String labelWhenNoLyricsTextColor = prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text_color), "Red");
        binding.lyricsView.setLabelShownWhenNoLyricsTextColor(colorInStringToDex(labelWhenNoLyricsTextColor));

        int labelWhenNoLyricsTextSize = prefs.getInt(getString(R.string.prefs_key_lyrics_not_available_text_size), 26);
        binding.lyricsView.setLabelShownWhenNoLyricsTextSize(sp2pix(labelWhenNoLyricsTextSize));

        String heightOfRefPitch = prefs.getString(getString(R.string.prefs_key_ref_pitch_stick_height), "6dp");
        binding.scoringView.setRefPitchStickHeight(dp2pix(Float.parseFloat(heightOfRefPitch.replace("dp", ""))));

        String defaultRefPitchStickColor = prefs.getString(getString(R.string.prefs_key_default_ref_pitch_stick_color), "Default");
        binding.scoringView.setRefPitchStickDefaultColor(colorInStringToDex(defaultRefPitchStickColor));

        String highlightedRefPitchStickColor = prefs.getString(getString(R.string.prefs_key_highlighted_ref_pitch_stick_color), "Default");
        binding.scoringView.setRefPitchStickHighlightedColor(colorInStringToDex(highlightedRefPitchStickColor));

        boolean particleEffectOn = prefs.getBoolean(getString(R.string.prefs_key_particle_effect_switch), true);
        binding.scoringView.enableParticleEffect(particleEffectOn);

        boolean customizedIndicatorAndParticleOn = prefs.getBoolean(getString(R.string.prefs_key_customized_indicator_and_particle_switch), false);
        if (customizedIndicatorAndParticleOn) {
            Bitmap bitmap = drawableToBitmap(getDrawable(R.drawable.pitch_indicator));
            binding.scoringView.setLocalPitchIndicator(bitmap);
            setParticles(false);
        } else {
            binding.scoringView.setLocalPitchIndicator(null);
            setParticles(true);
        }

        float particleHitOnThreshold = prefs.getFloat(getString(R.string.prefs_key_particle_hit_on_threshold), 0.8f);
        binding.scoringView.setThresholdOfHitScore(particleHitOnThreshold);
    }

    private void setParticles(boolean defaultOrCustomized) {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (defaultOrCustomized) {
                    binding.scoringView.setParticles(null);
                } else {
                    Drawable[] drawables = new Drawable[]{getDrawable(R.drawable.pitch_indicator), getDrawable(R.drawable.pitch_indicator_yellow), getDrawable(R.drawable.ic_launcher_background), getDrawable(R.drawable.star7), getDrawable(R.drawable.star8)};
                    binding.scoringView.setParticles(drawables);
                }
            }
        }, 500);
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private int dp2pix(float dp) {
        float density = getResources().getDisplayMetrics().scaledDensity;
        return (int) (dp * density);
    }

    private int sp2pix(float sp) {
        float density = getResources().getDisplayMetrics().scaledDensity;
        return (int) (sp * density + 0.5);
    }

    private int colorInStringToDex(String color) {
        int colorInDex = 0;
        switch (color) {
            case "Yellow":
                colorInDex = Color.YELLOW;
                break;
            case "White":
                colorInDex = Color.WHITE;
                break;
            case "Red":
                colorInDex = Color.RED;
                break;
            case "Gray":
                colorInDex = Color.parseColor("#9E9E9E");
                break;
            case "Orange":
                colorInDex = Color.parseColor("#FFA500");
                break;
            case "Blue":
                colorInDex = Color.BLUE;
                break;
            case "Brown":
                colorInDex = Color.parseColor("#654321");
                break;
            case "Green":
                colorInDex = Color.GREEN;
                break;
            default:
                colorInDex = 0;
                break;
        }
        return colorInDex;
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

    private void doClearCacheAndLoadSongCode() {
        if (mMccExService) {
            mMccExManager.stop();
            mCurrentSongCodeIndex++;
            if (mCurrentSongCodeIndex >= LyricsResourcePool.asMusicListEx().size()) {
                mCurrentSongCodeIndex = 0;
            }
        } else {
            mMusicContentCenterManager.stop();
            mCurrentSongCodeIndex++;
            if (mCurrentSongCodeIndex >= LyricsResourcePool.asMusicList().size()) {
                mCurrentSongCodeIndex = 0;
            }
        }

        doPlay();
    }


    private void updatePlayingProgress(final long progress) {
        binding.playingProgress.setText(String.valueOf(progress));
    }

    private void updateCallback(final String callback) {
        binding.callBack.setText(callback);
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    private void doPlay() {
        mLyricsCurrentProgress = 0;
        if (mMccExService) {
            mMccExManager.preloadMusic(LyricsResourcePool.asMusicListEx().get(mCurrentSongCodeIndex).songId, "");
        } else {
            mMusicContentCenterManager.preloadMusic(LyricsResourcePool.asMusicList().get(mCurrentSongCodeIndex).songCode);
        }
    }

    private void doStop() {
        mLyricsCurrentProgress = 0;
        if (mMccExService) {
            mMccExManager.stop();
        } else {
            mMusicContentCenterManager.stop();
        }
    }

    private void forwardToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        mLauncher.launch(intent);
    }

    private void skipTheIntro() {
        if (mLyricsModel == null || mLyricsCurrentProgress <= 0 || mLyricsCurrentProgress > mLyricsModel.lines.get(mLyricsModel.lines.size() - 1).getEndTime()) {
            Toast.makeText(getBaseContext(), "Not READY for SKIP INTRO, please Play first or no lyrics content", Toast.LENGTH_LONG).show();
            return;
        }
        mLyricsCurrentProgress = mLyricsModel.preludeEndPosition - 1000; // Jump to slight earlier

        if (mMccExService) {
            mMccExManager.seek(mLyricsCurrentProgress);
            mMccExManager.updateMusicPosition(mLyricsCurrentProgress);
        } else {
            mMusicContentCenterManager.seek(mLyricsCurrentProgress);
            mMusicContentCenterManager.updateMusicPosition(mLyricsCurrentProgress);
        }
        mState = Player_State.Playing;
    }

    private void doPauseOrResume() {
        if (mState == Player_State.Playing) {
            mState = Player_State.Pause;
        } else if (mState == Player_State.Pause) {
            mState = Player_State.Playing;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLyricsCurrentProgress = 0;
        mState = Player_State.Uninitialized;

        if (mKaraokeView != null) {
            mKaraokeView.setProgress(0);
            mKaraokeView.setPitch(0, 0);
            mKaraokeView.reset();
            mKaraokeView = null;
        }

        if (mFuture != null) {
            mFuture.cancel(true);
        }
        if (mMccExService) {
            mMccExManager.destroy();
        } else {
            mMusicContentCenterManager.destroy();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.play:
                if (mState == Player_State.Playing) {
                    binding.play.setText(this.getResources().getString(R.string.play));
                    doStop();
                } else {
                    binding.play.setText(this.getResources().getString(R.string.stop));
                    updateCallback("播放");
                    doPlay();
                }
                break;
            case R.id.pause:
                if (mState != Player_State.Playing && mState != Player_State.Pause) {
                    return;
                }
                if (mState == Player_State.Playing) {
                    updateCallback(this.getResources().getString(R.string.pause));
                    binding.pause.setText(this.getResources().getString(R.string.resume));
                    if (mMccExService) {
                        mMccExManager.pause();
                    } else {
                        mMusicContentCenterManager.pause();
                    }
                } else {
                    updateCallback(this.getResources().getString(R.string.resume));
                    binding.pause.setText(this.getResources().getString(R.string.pause));
                    if (mMccExService) {
                        mMccExManager.resume();
                    } else {
                        mMusicContentCenterManager.resume();
                    }
                }

                doPauseOrResume();
                break;
            case R.id.switch_to_next:
                updateCallback("Next");
                mLyricsCurrentProgress = 0; // Replay if already playing
                mState = Player_State.Playing;
                doClearCacheAndLoadSongCode();
                break;

            case R.id.skip_the_intro:
                updateCallback("Skip Intro");
                skipTheIntro();
                break;
            case R.id.settings:
                forwardToSettings();
                break;
            default:
                break;
        }
    }

    //======================= MusicContentCenterManager.MccCallback start =============
    @Override
    public void onMusicLyricRequest(long songCode, String lyricUrl) {
        if (TextUtils.isEmpty(lyricUrl)) {
            mLyricsModel = null;

            // Call this will trigger no/invalid lyrics ui
            mKaraokeView.setLyricData(null);

            playMusic();
            updateLyricsDescription();
        } else {
            loadTheLyrics(lyricUrl, null);
        }
    }

    @Override
    public void onMusicPreloadResult(long songCode, int percent) {
        updateCallback("Preload: " + songCode + "  " + percent + "%");
    }

    @Override
    public void onMusicPositionChange(long position) {
        mLyricsCurrentProgress = position;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updatePlayingProgress(position);
            }
        });
        if (mState == Player_State.Playing) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mKaraokeView.setProgress(position);
                }
            });
        }
    }

    @Override
    public void onMusicPitch(double voicePitch) {
        if (mState == Player_State.Playing) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mKaraokeView.setPitch((float) voicePitch, 0);
                }
            });

        }
    }

    @Override
    public void onMusicPlaying() {
        updateCallback("Playing");
        mState = Player_State.Playing;
    }

    @Override
    public void onMusicStop() {
        updateCallback("Stop");
        mState = Player_State.Idle;
    }
    //======================= MusicContentCenterManager.MccCallback end =============

    //========== MccExManager.MccExCallback start=============================
    @Override
    public void onInitializeResult(@NonNull MccExState state, @NonNull MccExStateReason reason) {

    }

    @Override
    public void onPreLoadEvent(@NonNull String requestId, long songCode, int percent, @NonNull String lyricPath, @NonNull String pitchPath, int offsetBegin, int offsetEnd, @NonNull MccExState state, @NonNull MccExStateReason reason) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (percent == 100 && state == MccExState.PRELOAD_STATE_COMPLETED) {
                    loadTheLyrics(lyricPath, pitchPath);
                }

                updateCallback("Preload: " + songCode + "  " + percent + "%");
            }
        });
    }

    @Override
    public void onLineScore(long songCode, @NonNull LineScoreData value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCallback("score=" + value.getLinePitchScore() + ", cumulatedScore=" + value.getCumulativeTotalLinePitchScores() + ", index=" + value.getPerformedLineIndex() + ", total=" + value.getPerformedTotalLines());
            }
        });
    }

    @Override
    public void onLyricResult(@NonNull String requestId, long songCode, @NonNull String lyricPath, int offsetBegin, int offsetEnd, @NonNull MccExStateReason reason) {
    }

    @Override
    public void onPitchResult(@NonNull String requestId, long songCode, @NonNull String pitchPath, int offsetBegin, int offsetEnd, @NonNull MccExStateReason reason) {
    }

    @Override
    public void onPlayStateChange() {
    }

    @Override
    public void onPitch(long songCode, @NonNull RawScoreData data) {
        mKaraokeView.setPitch(data.getSpeakerPitch(), data.getProgressInMs());
    }

    //========== MccExManager.MccExCallback end=============================
}