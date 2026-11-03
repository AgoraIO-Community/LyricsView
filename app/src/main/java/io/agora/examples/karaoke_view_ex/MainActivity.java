package io.agora.examples.karaoke_view_ex;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
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

import java.util.Objects;

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
import io.agora.rtc2.IRtcEngineEventHandler;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ServiceManager.ServiceCallback, SongSelectionSheet.Host {
    private static final String TAG = Constants.TAG + "-Main";
    private ActivityMainBinding binding;
    private KaraokeView mKaraokeView;
    private TmeSingingPresenter mTmeSinging;
    private LyricModel mLyricsModel;
    private ActivityResultLauncher<Intent> mLauncher;
    private long mLyricsCurrentProgress = 0;
    private boolean mSetNoLyric = false;
    private boolean mUseInternalScoring = true;
    private int mTmeResourceRequestId = 0;
    private String mTmeSongName = "";
    private boolean mSwitchingMusic = false;
    private PlayerState mState = PlayerState.UNINITIALIZED;

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

        mKaraokeView = new KaraokeView(binding.enableLyrics.isChecked() ? binding.lyricsView : null, binding.enableScoring.isChecked() ? binding.scoringView : null);

        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override public boolean onSeekRequested(KaraokeView view, long position) {
                if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) return mTmeSinging.seek(position);
                onDragTo(view, position);
                return true;
            }

            @Override
            public void onDragTo(KaraokeView view, long position) {
                if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
                    mTmeSinging.seek(position);
                    return;
                }
                mKaraokeView.setProgress(position);
                mLyricsCurrentProgress = position;
                updateCallback("Dragging, new progress " + position);
                ServiceManager.INSTANCE.seek(position);
            }

            @Override
            public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
                    mTmeSinging.lineFinished(score, cumulativeScore, lineCount);
                    return;
                }
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
                if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
                    mTmeSinging.attachUi();
                    return;
                }
                if (mLyricsCurrentProgress <= 0) {
                    return;
                }

                mKaraokeView.attachUi(isChecked ? binding.lyricsView : null, binding.enableScoring.isChecked() ? binding.scoringView : null);
            }
        });

        binding.enableScoring.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
                    mTmeSinging.attachUi();
                    return;
                }
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

        mTmeSinging = new TmeSingingPresenter(this, binding, mKaraokeView, savedInstanceState, getIntent().getExtras());
        loadPreferences();
        initDownloader();
    }

    protected void applyInitialServiceSelection(Bundle savedInstanceState) {
        ServiceType initial = DemoServicePreferences.normalize(this);
        if (ServiceManager.INSTANCE.getServiceType() != initial) {
            ServiceManager.INSTANCE.destroy();
            ServiceManager.INSTANCE.setServiceType(initial);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) mTmeSinging.start();
    }

    @Override protected void onStop() {
        if (mTmeSinging != null && ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) mTmeSinging.background();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) mTmeSinging.save(outState);
        super.onSaveInstanceState(outState);
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

    private void loadTheLyrics(String lrcUri) {
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
            return;
        }
        Log.i(TAG, "loadTheLyrics " + lrcUri);
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
        } else {
            LyricsFileDownloader.getInstance(this).download(lrcUri);
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
        int serviceType = DemoServicePreferences.normalize(this).getType();
        Log.i(TAG, "loadPreferences serviceType: " + serviceType);
        if (serviceType != Objects.requireNonNull(ServiceManager.INSTANCE.getServiceType()).getType()) {
            mTmeSinging.clear();
            doStop();
            mKaraokeView.setLyricData(null, mUseInternalScoring);
            mKaraokeView.reset();
            mTmeResourceRequestId++;
            mTmeSongName = "";
            mLyricsModel = null;
            mLyricsCurrentProgress = 0;
            mSwitchingMusic = false;
            mState = PlayerState.IDLE;
            binding.pause.setText(R.string.pause);
            binding.play.setText(R.string.play);
            binding.lyricsDescription.setText(R.string.choose_song_prompt);
            binding.callBack.setText("");
            binding.playingProgress.setText("");
            ServiceManager.INSTANCE.destroy();
            ServiceManager.INSTANCE.setServiceType(Objects.requireNonNull(ServiceType.Companion.fromType(serviceType)));
            ServiceManager.INSTANCE.initService(this.getApplicationContext(), this);
        }

        mUseInternalScoring = true;
        int lyricType = prefs.getInt(getString(R.string.prefs_key_lyric_type), LyricType.XML.ordinal());
        ServiceManager.INSTANCE.setLyricType(lyricType);
        boolean isTme = ServiceManager.INSTANCE.getServiceType() == ServiceType.TME;
        setTitle(isTme ? R.string.tme_singing_title : R.string.mcc_singing_title);
        if (isTme) {
            mTmeSinging.applyPreferences(mSetNoLyric);
            if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) mTmeSinging.start();
        } else {
            mKaraokeView.setLyricData(mSetNoLyric ? null : mLyricsModel, mUseInternalScoring);
            enableView(true);
        }

        KaraokeViewStyle.apply(this, binding.lyricsView, binding.scoringView);
        boolean lyricsDraggingOn = prefs.getBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), true);
        if (isTme) mTmeSinging.updateDragging();
        else binding.lyricsView.enableDragging(lyricsDraggingOn);
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
        binding.playOriginal.setText(ServiceManager.INSTANCE.isOriginalPlay() ? R.string.play_accompany : R.string.play_original);
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

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.singing_navigation, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_choose_song) {
            SongSelectionSheet.show(getSupportFragmentManager(), Objects.requireNonNull(ServiceManager.INSTANCE.getServiceType()));
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            forwardToSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override public boolean onTmeSongSelected(@NonNull MccManager.TmeSong song) {
        return ServiceManager.INSTANCE.getServiceType() == ServiceType.TME && mTmeSinging.selectSong(song.songId, song.name);
    }

    @Override public boolean onMccSongSelected(int position) {
        if (!ServiceManager.INSTANCE.selectMccSong(position)) return false;
        mSwitchingMusic = true;
        ServiceManager.INSTANCE.stop();
        doPlay();
        return true;
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
        if (mTmeSinging != null) mTmeSinging.destroy();
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

    @Override
    public void onClick(View view) {
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
            switch (view.getId()) {
                case R.id.play: mTmeSinging.togglePlay(); break;
                case R.id.pause: mTmeSinging.pauseResume(); break;
                case R.id.switch_to_next: mTmeSinging.next(); break;
                case R.id.skip_the_intro: mTmeSinging.skipIntro(); break;
                case R.id.play_original:
                    ServiceManager.INSTANCE.doPlayOriginal();
                    binding.playOriginal.setText(ServiceManager.INSTANCE.isOriginalPlay() ? R.string.play_accompany : R.string.play_original);
                    break;
                default: break;
            }
            return;
        }
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
                ServiceManager.INSTANCE.doPlayOriginal();
                binding.playOriginal.setText(ServiceManager.INSTANCE.isOriginalPlay() ? R.string.play_accompany : R.string.play_original);
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
    }

    @Override
    public void onMusicLyricRequest(long songCode, @Nullable String lyricUrl, @Nullable String pitchUrl) {
        runOnUiThread(() -> loadTheLyrics(lyricUrl));
    }

    @Override
    public void onMusicPreloadResult(long songCode, int percent) {
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) return;
        runOnUiThread(() -> updateCallback("Preload: " + songCode + "  " + percent + "%"));
    }

    @Override
    public void onTmeSongChanged(@NonNull String songName) {
        // The shared presenter owns TME song/session messages.
    }

    @Override
    public void onTmeError(@NonNull String message) {
        runOnUiThread(() -> {
            if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) {
                updateCallback(message);
                return;
            }
            mSwitchingMusic = false;
            mState = PlayerState.IDLE;
            binding.play.setText(R.string.play);
            updateCallback(message);
        });
    }

    @Override
    public void onMusicPositionChange(long position) {
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) return;
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
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) return;
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
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) return;
        runOnUiThread(() -> {
            updateCallback("Playing");
            mState = PlayerState.PLAYING;
            mSwitchingMusic = false;
            binding.play.setText(R.string.stop);
        });
    }

    @Override
    public void onMusicStop() {
        if (ServiceManager.INSTANCE.getServiceType() == ServiceType.TME) return;
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
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }
}
