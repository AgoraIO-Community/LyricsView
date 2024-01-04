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
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.agora.examples.karaoke_view.databinding.ActivityMainBinding;
import io.agora.examples.utils.MusicContentCenterManager;
import io.agora.examples.utils.ResourceHelper;
import io.agora.karaoke_view.v11.KaraokeEvent;
import io.agora.karaoke_view.v11.KaraokeView;
import io.agora.karaoke_view.v11.constants.Constants;
import io.agora.karaoke_view.v11.constants.DownloadError;
import io.agora.karaoke_view.v11.downloader.LyricsFileDownloader;
import io.agora.karaoke_view.v11.downloader.LyricsFileDownloaderCallback;
import io.agora.karaoke_view.v11.model.LyricsLineModel;
import io.agora.karaoke_view.v11.model.LyricsModel;
import io.agora.logging.ConsoleLogger;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, MusicContentCenterManager.MccCallback {

    private ActivityMainBinding binding;

    private static final String TAG = Constants.TAG + "-Main";

    private KaraokeView mKaraokeView;

    private int mCurrentIndex = 0;
    private int mCurrentSongCodeIndex = 0;

    private LyricsModel mLyricsModel;

    private ActivityResultLauncher mLauncher;

    private Handler mHandler;
    private MusicContentCenterManager mMusicContentCenterManager;
    private boolean mIsMockPlay = false;
    private ConsoleLogger mConsoleLogger;
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

        mMusicContentCenterManager = new MusicContentCenterManager(this, this);
        mMusicContentCenterManager.init();

        mHandler = new Handler(this.getMainLooper());

        binding.switchToNext.setOnClickListener(this);
        binding.playMock.setOnClickListener(this);
        binding.play.setOnClickListener(this);
        binding.pause.setOnClickListener(this);
        binding.skipTheIntro.setOnClickListener(this);
        binding.settings.setOnClickListener(this);

        mKaraokeView = new KaraokeView(binding.enableLyrics.isChecked() ? binding.lyricsView : null, binding.enableScoring.isChecked() ? binding.scoringView : null);

        if (null == mConsoleLogger) {
            mConsoleLogger = new ConsoleLogger();
        }
        mKaraokeView.addLogger(mConsoleLogger);

        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
                mLyricsCurrentProgress = position;
                updateCallback("Dragging, new progress " + position);
                if (!mIsMockPlay) {
                    mMusicContentCenterManager.seek(position);
                    mMusicContentCenterManager.updateMusicPosition(position);
                }
            }

            @Override
            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches) {
                if (mIsMockPlay) {
                    mKaraokeView.setPitch(refPitch);
                }
            }

            @Override
            public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulatedScore, int index, int total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCallback("score=" + score + ", cumulatedScore=" + cumulatedScore + ", index=" + index + ", total=" + total);
                    }
                });
            }
        });

        //loadTheLyrics(LyricsResourcePool.asList().get(mCurrentIndex).lyrics, LyricsResourcePool.asList().get(mCurrentIndex).pitches);

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
                    mLyricsModel = KaraokeView.parseLyricsData(fileData);

                    if (mLyricsModel != null) {
                        mKaraokeView.setLyricsData(mLyricsModel);
                    }
                    playMusic();
                    updateLyricsDescription();
                } else {
                    mLyricsModel = null;

                    String description = LyricsResourcePool.asList().get(mCurrentIndex).description; // For Testing
                    if (description != null && description.contains("SHOW_NO_LYRICS_TIPS")) {
                        mKaraokeView.setLyricsData(null); // Call this will trigger no/invalid lyrics ui
                    }
                    playMusic();
                    updateLyricsDescription();
                }
            }
        });
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
            mKaraokeView.setLyricsData(null);
            playMusic();
            updateLyricsDescription();
        } else if (lrcUri.startsWith("https://") || lrcUri.startsWith("http://")) {
            LyricsFileDownloader.getInstance(this).download(lrcUri);
        } else {
            File lrc = ResourceHelper.copyAssetsToCreateNewFile(getApplicationContext(), lrcUri);
            File pitch = ResourceHelper.copyAssetsToCreateNewFile(getApplicationContext(), pitchUri);
            lrc = extractFromZipFileIfPossible(lrc);
            mLyricsModel = KaraokeView.parseLyricsData(lrc, pitch);

            if (mLyricsModel != null) {
                mKaraokeView.setLyricsData(mLyricsModel);
            }
            playMusic();
            updateLyricsDescription();
        }
    }

    private void updateLyricsDescription() {
        String lyricsSummary = mLyricsModel != null ? (mLyricsModel.title + ": " + mLyricsModel.artist + " " + mLyricsModel.startOfVerse + " " + mLyricsModel.lines.size() + " " + mLyricsModel.duration) : "Invalid lyrics";
        Log.d(TAG, "lyricsSummary: " + lyricsSummary);
        String description = lyricsSummary + "\n";
        if (mIsMockPlay) {
            description += LyricsResourcePool.asList().get(mCurrentIndex).description;
        }
        final String finalDescription = description;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tvDescription = findViewById(R.id.lyrics_description);
                tvDescription.setText(finalDescription);
            }
        });
    }

    private void playMusic() {
        if (!mIsMockPlay) {
            mMusicContentCenterManager.openMusic(LyricsResourcePool.asMusicList().get(mCurrentSongCodeIndex).songCode);
        }
    }

    private void loadPreferences() {
        Log.i(TAG, "loadPreferences");
        SharedPreferences prefs = getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE);
        int scoringLevel = prefs.getInt(getString(R.string.prefs_key_scoring_level), mKaraokeView.getScoringLevel());
        mKaraokeView.setScoringLevel(scoringLevel);
        int scoringOffset = prefs.getInt(getString(R.string.prefs_key_scoring_compensation_offset), mKaraokeView.getScoringCompensationOffset());
        mKaraokeView.setScoringCompensationOffset(scoringOffset);

        boolean indicatorOn = prefs.getBoolean(getString(R.string.prefs_key_start_of_verse_indicator_switch), true);
        binding.lyricsView.enableStartOfVerseIndicator(indicatorOn);
        String indicatorColor = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_color), "Default");
        binding.lyricsView.setStartOfVerseIndicatorColor(colorInStringToDex(indicatorColor));
        String indicatorRadius = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_radius), "6dp");
        binding.lyricsView.setStartOfVerseIndicatorRadius(dp2pix(Float.parseFloat(indicatorRadius.replace("dp", ""))));
        int indicatorPaddingTop = prefs.getInt(getString(R.string.prefs_key_start_of_verse_indicator_padding_top), 6);
        binding.lyricsView.setStartOfVerseIndicatorPaddingTop(dp2pix(indicatorPaddingTop));

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

    private void doClearCacheAndLoadTheLyrics() {
        mCurrentIndex++;
        if (mCurrentIndex >= LyricsResourcePool.asList().size()) {
            mCurrentIndex = 0;
        }

        mExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                updateLyricsDescription();

                loadTheLyrics(LyricsResourcePool.asList().get(mCurrentIndex).lyrics, LyricsResourcePool.asList().get(mCurrentIndex).pitches);
            }
        }, 0, TimeUnit.MILLISECONDS);
    }

    private void doClearCacheAndLoadSongCode() {
        mMusicContentCenterManager.stop();
        mCurrentSongCodeIndex++;
        if (mCurrentSongCodeIndex >= LyricsResourcePool.asMusicList().size()) {
            mCurrentSongCodeIndex = 0;
        }

        doPlay();
    }

    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    private long mLyricsCurrentProgress = 0;

    private void updatePlayingProgress(final long progress) {
        binding.playingProgress.setText(String.valueOf(progress));
    }

    private void updateCallback(final String callback) {
        binding.callBack.setText(callback);
    }

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

    private void doMockPlay() {
        if (mLyricsModel == null) {
            Toast.makeText(getBaseContext(), "Not READY for Play, please switch again", Toast.LENGTH_LONG).show();
            mLyricsCurrentProgress = 0;
            mState = Player_State.Uninitialized;
            mKaraokeView.setProgress(0);
            mKaraokeView.setPitch(0);
            if (mFuture != null) {
                mFuture.cancel(true);
            }
            return;
        }

        final long DURATION_OF_SONG = mLyricsModel.lines.get(mLyricsModel.lines.size() - 1).getEndTime();
        mLyricsCurrentProgress = 0;
        mState = Player_State.Idle;
        mKaraokeView.reset();
        mKaraokeView.setLyricsData(mLyricsModel);
        final String PLAYER_TAG = TAG + "_MockPlayer";
        Log.d(PLAYER_TAG, "duration: " + DURATION_OF_SONG + ", progress: " + mLyricsCurrentProgress + " " + mFuture);
        if (mFuture != null) {
            mFuture.cancel(true);
        }

        mFuture = mExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (mState == Player_State.Pause) { // mock player pausing
                    return;
                }

                if (mLyricsCurrentProgress >= 0 && mLyricsCurrentProgress < DURATION_OF_SONG) {
                    mKaraokeView.setProgress(mLyricsCurrentProgress); // zero for first time
                    //Log.d(PLAYER_TAG, "timer mCurrentPosition: " + mLyricsCurrentProgress + " " + Thread.currentThread());
                } else if (mLyricsCurrentProgress >= DURATION_OF_SONG && mLyricsCurrentProgress < (DURATION_OF_SONG + 1000)) {
                    long lastPosition = mLyricsCurrentProgress;
                    mKaraokeView.setProgress(mLyricsCurrentProgress);
                    mKaraokeView.setPitch(0);
                    Log.d(PLAYER_TAG, "put the indicator back in space");
                    // Put the indicator back in space
                } else if (mLyricsCurrentProgress >= (DURATION_OF_SONG + 1000)) {
                    if (mFuture != null) {
                        mFuture.cancel(true);
                    }
                    mLyricsCurrentProgress = 0;
                    mState = Player_State.Idle;
                    mKaraokeView.reset();
                    Log.d(PLAYER_TAG, "quit");
                    return;
                }
                mLyricsCurrentProgress += 20;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updatePlayingProgress(mLyricsCurrentProgress);
                    }
                });
            }
        }, 0, 20, TimeUnit.MILLISECONDS);

        mState = Player_State.Playing;
    }

    private void doPlay() {
        mLyricsCurrentProgress = 0;
        mMusicContentCenterManager.preloadMusic(LyricsResourcePool.asMusicList().get(mCurrentSongCodeIndex).songCode);
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
        mLyricsCurrentProgress = mLyricsModel.startOfVerse - 1000; // Jump to slight earlier
        if (!mIsMockPlay) {
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

        if (mConsoleLogger != null && mKaraokeView != null) {
            mKaraokeView.removeLogger(mConsoleLogger);
        }

        if (mKaraokeView != null) {
            mKaraokeView.setProgress(0);
            mKaraokeView.setPitch(0);
            mKaraokeView.reset();
            mKaraokeView = null;
        }

        if (mFuture != null) {
            mFuture.cancel(true);
        }

        mMusicContentCenterManager.destroy();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_to_next:
                updateCallback("Next");
                mLyricsCurrentProgress = 0; // Replay if already playing
                mState = Player_State.Playing;
                if (mIsMockPlay) {
                    doClearCacheAndLoadTheLyrics();
                } else {
                    doClearCacheAndLoadSongCode();
                }
                break;
            case R.id.play_mock:
                updateCallback("Play(Mock)");
                if (!mIsMockPlay && mState == Player_State.Playing) {
                    mMusicContentCenterManager.stop();
                }
                mIsMockPlay = true;
                doMockPlay();
                break;
            case R.id.play:
                mIsMockPlay = false;
                updateCallback("Play(MCC)");
                doPlay();
                break;
            case R.id.pause:
                if (mState != Player_State.Playing && mState != Player_State.Pause) {
                    return;
                }
                if (mState == Player_State.Playing) {
                    updateCallback("Pause");
                    binding.pause.setText("Resume");
                    if (!mIsMockPlay) {
                        mMusicContentCenterManager.pause();
                    }
                } else {
                    updateCallback("Resume");
                    binding.pause.setText("Pause");
                    if (!mIsMockPlay) {
                        mMusicContentCenterManager.resume();
                    }
                }

                doPauseOrResume();
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

    @Override
    public void onMusicLyricRequest(long songCode, String lyricUrl) {
        if (TextUtils.isEmpty(lyricUrl)) {
            mLyricsModel = null;

            // Call this will trigger no/invalid lyrics ui
            mKaraokeView.setLyricsData(null);

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
                    mKaraokeView.setPitch((float) voicePitch);
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
}