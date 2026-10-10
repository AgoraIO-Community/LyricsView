package io.agora.examples.karaoke_view_ex;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

import io.agora.examples.karaoke_view_ex.agora.RtcManager;
import io.agora.examples.karaoke_view_ex.agora.ServiceManager;
import io.agora.examples.karaoke_view_ex.agora.MccManager;
import io.agora.examples.karaoke_view_ex.databinding.ActivityMainBinding;
import io.agora.examples.utils.ServiceType;
import io.agora.karaoke_view_ex.KaraokeEvent;
import io.agora.karaoke_view_ex.KaraokeView;
import io.agora.karaoke_view_ex.constants.Constants;
import io.agora.karaoke_view_ex.constants.DownloadError;
import io.agora.karaoke_view_ex.downloader.LyricsFileDownloader;
import io.agora.karaoke_view_ex.downloader.LyricsFileDownloaderCallback;
import io.agora.karaoke_view_ex.internal.constants.LyricType;
import io.agora.karaoke_view_ex.internal.model.LyricsLineModel;
import io.agora.karaoke_view_ex.model.LyricModel;
import io.agora.karaoke_view_ex.utils.LyricsCutter;
import io.agora.rtc2.IRtcEngineEventHandler;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ServiceManager.ServiceCallback {
    public static final String EXTRA_SERVICE_TYPE = "serviceType";
    private static final String TAG = Constants.TAG + "-Main";
    private ActivityMainBinding binding;
    private KaraokeView mKaraokeView;
    private LyricModel mLyricsModel;
    private ActivityResultLauncher mLauncher;
    private long mLyricsCurrentProgress = 0;
    private boolean mSetNoLyric = false;
    private boolean mUseInternalScoring = false;
    private int mTmeResourceRequestId = 0;
    private String mTmeSongName = "";
    private boolean mSwitchingMusic = false;
    private PlayerState mState = PlayerState.UNINITIALIZED;
    private List<MccManager.TmeSong> mSongList = new ArrayList<>();
    private final ServiceManager.TmeListListener mListListener = (songs, loading, error) -> {
        if (binding == null || ServiceManager.INSTANCE.getServiceType() != ServiceType.TME) return;
        mSongList = new ArrayList<>(songs);
        List<String> titles = new ArrayList<>();
        for (MccManager.TmeSong song : songs) titles.add(song.name);
        binding.tmeSongList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles));
        binding.tmeListStatus.setText(loading ? getString(R.string.tme_loading_songs)
                : error != null ? error : getString(R.string.tme_choose_song));
        binding.tmeListRetry.setEnabled(!loading);
        binding.tmeSongList.setEnabled(!loading);
    };

    private enum PlayerState {
        UNINITIALIZED(-1),
        IDLE(0),
        PLAYING(1),
        PAUSE(2);

        private int state;

        private PlayerState(int def) {
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
        applyInitialServiceSelection(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        enableView(false);

        initRtc();
        ServiceManager.INSTANCE.initService(this.getApplicationContext(), this);
        enableView(true);

        binding.switchToNext.setOnClickListener(this);
        binding.play.setOnClickListener(this);
        binding.pause.setOnClickListener(this);
        binding.skipTheIntro.setOnClickListener(this);
        binding.playOriginal.setOnClickListener(this);
        binding.settings.setOnClickListener(this);
        binding.tmeListRetry.setOnClickListener(view -> ServiceManager.INSTANCE.retryTmeSongs());
        binding.tmeSongList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= mSongList.size()) return;
            MccManager.TmeSong song = mSongList.get(position);
            String session = ServiceManager.INSTANCE.startTmeSong(getApplicationContext(), song.songId, song.name);
            if (session == null) return;
            Intent intent = new Intent(this, TmeSingingActivity.class);
            intent.putExtra("sessionId", session);
            intent.putExtra("songId", song.songId);
            intent.putExtra("songName", song.name);
            startActivity(intent);
        });

        mKaraokeView = new KaraokeView(binding.enableLyrics.isChecked() ? binding.lyricsView : null, binding.enableScoring.isChecked() ? binding.scoringView : null);

        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
                mLyricsCurrentProgress = position;
                updateCallback("Dragging, new progress " + position);
                ServiceManager.INSTANCE.seek(position);
            }

            @Override
            public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCallback("score=" + score + ", cumulatedScore=" + cumulativeScore + ", index=" + index + ", lineCount=" + lineCount);
                    }
                });
            }
        });


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

        loadPreferences();
        initDownloader();
    }

    private void applyInitialServiceSelection(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE);
        if (savedInstanceState == null && getIntent().hasExtra(EXTRA_SERVICE_TYPE)) {
            ServiceType selected = ServiceType.Companion.fromType(getIntent().getIntExtra(EXTRA_SERVICE_TYPE, -1));
            if (selected == ServiceType.MCC || selected == ServiceType.TME) {
                prefs.edit()
                        .putInt(getString(R.string.prefs_key_service_type), selected.getType())
                        .putInt(getString(R.string.prefs_key_lyric_type),
                                selected == ServiceType.TME ? LyricType.LRC.ordinal() : LyricType.XML.ordinal())
                        .apply();
            }
        }
        ServiceType initial = ServiceType.Companion.fromType(
                prefs.getInt(getString(R.string.prefs_key_service_type), ServiceType.MCC.getType()));
        if (initial == null) {
            initial = ServiceType.MCC;
            prefs.edit().putInt(getString(R.string.prefs_key_service_type), initial.getType()).apply();
        }
        if (ServiceManager.INSTANCE.getServiceType() != initial) {
            ServiceManager.INSTANCE.destroy();
            ServiceManager.INSTANCE.setServiceType(initial);
        }
    }

    private void initRtc() {
        RtcManager.INSTANCE.initRtcEngine(MainActivity.this, new RtcManager.RtcCallback() {

            @Override
            public void onAudioVolumeIndication(@Nullable IRtcEngineEventHandler.AudioVolumeInfo[] speakers, int totalVolume) {
                if (null != speakers && (mState == PlayerState.PLAYING || ServiceManager.INSTANCE.getServiceType() == ServiceType.TME)) {
                    for (IRtcEngineEventHandler.AudioVolumeInfo audioVolumeInfo : speakers) {
                        if (audioVolumeInfo.uid == 0) {
                            ServiceManager.INSTANCE.updateSpeakerPitch(audioVolumeInfo.voicePitch);
                            break;
                        }
                    }
                }
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
    }

    private void initDownloader() {
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
                    //mLyricsModel = KaraokeView.parseLyricData(new File("/storage/emulated/0/Android/data/io.agora.examples.karaoke_view_ex/cache/e6fa1ebb_.lrc"), null);
                    //mLyricsModel = KaraokeView.parseLyricData(new File("/storage/emulated/0/Android/data/io.agora.examples.karaoke_view_ex/cache/745012"), null);

                    mUseInternalScoring = true;
                    if (mLyricsModel != null && !mSetNoLyric) {
                        mKaraokeView.setLyricData(mLyricsModel, true);
                    } else {
                        mKaraokeView.setLyricData(null, true);
                    }
                }
                ServiceManager.INSTANCE.openMusic();
                updateLyricsDescription();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!EasyPermissions.hasPermissions(this, PERMISSION)) {
            EasyPermissions.requestPermissions(this, getString(R.string.error_permission),
                    TAG_PERMISSION_REQUEST_CODE, PERMISSION);
        }
    }

    private void loadTheLyrics(String lrcUri, String pitchUri, int songOffsetBegin, int songOffsetEnd, int lyricOffset) {
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
            return;
        }
        Log.i(TAG, "loadTheLyrics " + lrcUri + " " + pitchUri + " " + lyricOffset);
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
            mKaraokeView.setLyricData(null, mUseInternalScoring);
            ServiceManager.INSTANCE.openMusic();
            updateLyricsDescription();
        } else if (lrcUri.startsWith("https://") || lrcUri.startsWith("http://")) {
            LyricsFileDownloader.getInstance(this).download(lrcUri);
        } else {
            File lrc;
            File pitch;
            lrc = new File(lrcUri);
            pitch = new File(pitchUri);
            mLyricsModel = KaraokeView.parseLyricData(lrc, pitch, true, lyricOffset);
            mUseInternalScoring = false;
            if (mSetNoLyric) {
                mKaraokeView.setLyricData(null, false);
            } else {
                if (BuildConfig.ENABLE_LYRIC_CUTTER && songOffsetBegin != 0) {
                    mLyricsModel = LyricsCutter.cut(mLyricsModel, songOffsetBegin - lyricOffset, songOffsetEnd - lyricOffset);
                }
                mKaraokeView.setLyricData(mLyricsModel, false);
            }
            ServiceManager.INSTANCE.openMusic();
            updateLyricsDescription();
        }
    }


    private void updateLyricsDescription() {
        String lyricsSummary = mLyricsModel != null
                ? ((mLyricsModel.name == null ? "" : mLyricsModel.name)
                + (mLyricsModel.singer == null ? "" : ": " + mLyricsModel.singer)
                + " " + mLyricsModel.preludeEndPosition + " " + mLyricsModel.lines.size() + " " + mLyricsModel.duration)
                : "Invalid lyrics";
        Log.d(TAG, "lyricsSummary: " + lyricsSummary);
        final String finalDescription = ServiceManager.INSTANCE.getServiceType() == ServiceType.TME
                ? "[TME] " + mTmeSongName + ((mLyricsModel == null || mLyricsModel.lines == null || mLyricsModel.lines.isEmpty()) ? "\nNo Lyrics Available" : "")
                : "[" + ServiceManager.INSTANCE.getServiceType() + "] " + lyricsSummary + "\n";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tvDescription = findViewById(R.id.lyrics_description);
                tvDescription.setText(finalDescription);
            }
        });
    }

    private void loadPreferences() {
        Log.i(TAG, "loadPreferences");
        SharedPreferences prefs = getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE);
        int scoringLevel = prefs.getInt(getString(R.string.prefs_key_scoring_level), mKaraokeView.getScoringLevel());
        mKaraokeView.setScoringLevel(scoringLevel);
        int scoringOffset = prefs.getInt(getString(R.string.prefs_key_scoring_compensation_offset), mKaraokeView.getScoringCompensationOffset());
        mKaraokeView.setScoringCompensationOffset(scoringOffset);

        mSetNoLyric = prefs.getBoolean(getString(R.string.prefs_key_set_no_lyric), false);
        if (mSetNoLyric) {
            mKaraokeView.setLyricData(null, mUseInternalScoring);
        } else {
            mKaraokeView.setLyricData(mLyricsModel, mUseInternalScoring);
        }

        int serviceType = prefs.getInt(getString(R.string.prefs_key_service_type), ServiceType.MCC.getType());
        Log.i(TAG, "loadPreferences serviceType: " + serviceType);
        if (serviceType != Objects.requireNonNull(ServiceManager.INSTANCE.getServiceType()).getType()) {
            if (mState == PlayerState.PLAYING) {
                updateCallback("停止");
                doStop();
            }
            mKaraokeView.setLyricData(null, mUseInternalScoring);
            mKaraokeView.reset();
            mTmeResourceRequestId++;
            mTmeSongName = "";
            ServiceManager.INSTANCE.destroy();
            ServiceManager.INSTANCE.setServiceType(Objects.requireNonNull(ServiceType.Companion.fromType(serviceType)));
            ServiceManager.INSTANCE.initService(this.getApplicationContext(), this);
        }

        int lyricType = prefs.getInt(getString(R.string.prefs_key_lyric_type), LyricType.XML.ordinal());
        ServiceManager.INSTANCE.setLyricType(lyricType);
        boolean isTme = ServiceManager.INSTANCE.getServiceType() == ServiceType.TME;
        setTitle(isTme ? R.string.tme_song_selection_title
                : ServiceManager.INSTANCE.getServiceType() == ServiceType.MCC ? R.string.library_mcc : R.string.app_name);
        for (int i = 0; i < binding.getRoot().getChildCount(); i++) {
            View child = binding.getRoot().getChildAt(i);
            if (child == binding.settings || child == binding.tmeListContainer) continue;
            child.setVisibility(isTme ? View.GONE : View.VISIBLE);
        }
        binding.tmeListContainer.setVisibility(isTme ? View.VISIBLE : View.GONE);
        if (isTme) ServiceManager.INSTANCE.attachTmeList(mListListener);
        else ServiceManager.INSTANCE.detachTmeList(mListListener);

        KaraokeViewStyle.apply(this, binding.lyricsView, binding.scoringView);
        boolean lyricsDraggingOn = prefs.getBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), true);
        binding.lyricsView.enableDragging(lyricsDraggingOn);
    }

    private void switchMusic() {
        if (!ServiceManager.INSTANCE.switchMusic()) {
            return;
        }
        mSwitchingMusic = true;
        ServiceManager.INSTANCE.stop();
        doPlay();
    }


    private void updatePlayingProgress(final long progress) {
        binding.playingProgress.setText(String.valueOf(progress));
    }

    private void updateCallback(final String callbackMessage) {
        binding.callBack.setText(callbackMessage);
    }

    private void doPlay() {
        if (!ServiceManager.INSTANCE.play()) {
            mSwitchingMusic = false;
            mState = PlayerState.IDLE;
            binding.play.setText(R.string.play);
            return;
        }
        binding.play.setText(this.getResources().getString(R.string.stop));
        mLyricsCurrentProgress = 0;
        binding.playOriginal.setText(this.getResources().getString(R.string.play_accompany));
    }

    private void doStop() {
        mTmeResourceRequestId++;
        binding.play.setText(this.getResources().getString(R.string.play));
        mLyricsCurrentProgress = 0;
        ServiceManager.INSTANCE.stop();
        mState = PlayerState.IDLE;
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
        // Jump to slight earlier
        mLyricsCurrentProgress = mLyricsModel.preludeEndPosition - 1000;
        ServiceManager.INSTANCE.seek(mLyricsCurrentProgress);
        mState = PlayerState.PLAYING;
    }

    private void doPauseOrResume() {
        if (mState == PlayerState.PLAYING) {
            mState = PlayerState.PAUSE;
        } else if (mState == PlayerState.PAUSE) {
            mState = PlayerState.PLAYING;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLyricsCurrentProgress = 0;
        mState = PlayerState.UNINITIALIZED;

        if (mKaraokeView != null) {
            mKaraokeView.setProgress(0);
            mKaraokeView.setPitch(0, 0, 0);
            mKaraokeView.reset();
            mKaraokeView = null;
        }

        if (isFinishing()) {
            ServiceManager.INSTANCE.destroy();
            RtcManager.INSTANCE.destroy();
        }
        mTmeResourceRequestId++;
    }

    @Override protected void onStart() {
        super.onStart();
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) ServiceManager.INSTANCE.attachTmeList(mListListener);
    }

    @Override protected void onStop() {
        ServiceManager.INSTANCE.detachTmeList(mListListener);
        super.onStop();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.play:
                if (mState == PlayerState.PLAYING) {
                    updateCallback("停止");
                    doStop();
                } else {
                    updateCallback("播放");
                    doPlay();
                }
                break;
            case R.id.pause:
                if (mState != PlayerState.PLAYING && mState != PlayerState.PAUSE) {
                    return;
                }
                if (mState == PlayerState.PLAYING) {
                    updateCallback(this.getResources().getString(R.string.pause));
                    binding.pause.setText(this.getResources().getString(R.string.resume));
                    ServiceManager.INSTANCE.pause();
                } else {
                    updateCallback(this.getResources().getString(R.string.resume));
                    binding.pause.setText(this.getResources().getString(R.string.pause));
                    ServiceManager.INSTANCE.resume();
                }

                doPauseOrResume();
                break;
            case R.id.switch_to_next:
                mLyricsCurrentProgress = 0; // Replay if already playing
                switchMusic();
                break;

            case R.id.skip_the_intro:
                updateCallback("Skip Intro");
                skipTheIntro();
                break;
            case R.id.play_original:
                if (ServiceManager.INSTANCE.isOriginalPlay()) {
                    binding.playOriginal.setText(this.getResources().getString(R.string.play_original));
                } else {
                    binding.playOriginal.setText(this.getResources().getString(R.string.play_accompany));
                }
                ServiceManager.INSTANCE.doPlayOriginal();
                break;
            case R.id.settings:
                forwardToSettings();
                break;
            default:
                break;
        }
    }

    private void enableView(boolean enable) {
        binding.play.setEnabled(enable);
        binding.pause.setEnabled(enable);
        binding.skipTheIntro.setEnabled(enable);
        binding.switchToNext.setEnabled(enable);
        binding.playOriginal.setEnabled(enable);
        binding.settings.setEnabled(enable);
    }

    @Override
    public void onMusicLyricRequest(long songCode, @Nullable String lyricUrl, @Nullable String pitchUrl, int songOffsetBegin, int songOffsetEnd, int lyricOffset) {
        runOnUiThread(() -> loadTheLyrics(lyricUrl, pitchUrl, songOffsetBegin, songOffsetEnd, lyricOffset));
    }

    @Override
    public void onMusicPreloadResult(long songCode, int percent) {
        runOnUiThread(() -> updateCallback("Preload: " + songCode + "  " + percent + "%"));
    }

    @Override
    public void onTmeSongChanged(@NonNull String songName) {
        runOnUiThread(() -> {
            mTmeSongName = songName;
            updateCallback("TME: " + songName);
        });
    }

    @Override
    public void onTmeError(@NonNull String message) {
        runOnUiThread(() -> {
            mSwitchingMusic = false;
            mState = PlayerState.IDLE;
            binding.play.setText(R.string.play);
            updateCallback(message);
        });
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
        if (mState == PlayerState.PLAYING) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mKaraokeView.setProgress(position);
                }
            });
        }
    }

    @Override
    public void onMusicPitch(double speakerPitch, long progressInMs) {
        if (mState == PlayerState.PLAYING && mLyricsModel != null && mLyricsModel.hasPitch) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mKaraokeView.setPitch((float) speakerPitch, 0F, (int) progressInMs);
                }
            });
        }
    }

    @Override
    public void onMusicPlaying() {
        runOnUiThread(() -> {
            updateCallback("Playing");
            mState = PlayerState.PLAYING;
            mSwitchingMusic = false;
            binding.play.setText(R.string.stop);
        });
    }

    @Override
    public void onMusicStop() {
        runOnUiThread(() -> {
            if (mSwitchingMusic) {
                return;
            }
            updateCallback("Stop");
            mState = PlayerState.IDLE;
            binding.play.setText(R.string.play);
            binding.pause.setText(R.string.pause);
        });
    }

    @Override
    public void onLineScore(long songCode, int score, int cumulatedScore, int lineIndex, int totalLine) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateCallback("score=" + score + ", cumulatedScore=" + cumulatedScore + ", index=" + lineIndex + ", total=" + totalLine);
            }
        });
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }
}
