package io.agora.examples.karaoke_view_ex;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.io.File;
import java.util.Objects;

import io.agora.examples.karaoke_view_ex.agora.RtcManager;
import io.agora.examples.karaoke_view_ex.agora.ServiceManager;
import io.agora.examples.karaoke_view_ex.databinding.ActivityMainBinding;
import io.agora.examples.utils.ServiceType;
import io.agora.examples.utils.Utils;
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

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ServiceManager.ServiceCallback {
    private static final String TAG = Constants.TAG + "-Main";
    private ActivityMainBinding binding;
    private KaraokeView mKaraokeView;
    private LyricModel mLyricsModel;
    private ActivityResultLauncher mLauncher;
    private long mLyricsCurrentProgress = 0;
    private boolean mSetNoLyric = false;
    private boolean mUseInternalScoring = false;
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

    private void initRtc() {
        RtcManager.INSTANCE.initRtcEngine(MainActivity.this, new RtcManager.RtcCallback() {

            @Override
            public void onAudioVolumeIndication(@Nullable IRtcEngineEventHandler.AudioVolumeInfo[] speakers, int totalVolume) {
                if (null != speakers && mState == PlayerState.PLAYING) {
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

    private void loadTheLyrics(String lrcUri, String pitchUri, int lyricOffset) {
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
                mKaraokeView.setLyricData(mLyricsModel, false);
            }
            ServiceManager.INSTANCE.openMusic();
            updateLyricsDescription();
        }
    }

    private void updateLyricsDescription() {
        String lyricsSummary = mLyricsModel != null ? (mLyricsModel.name + ": " + mLyricsModel.singer + " " + mLyricsModel.preludeEndPosition + " " + mLyricsModel.lines.size() + " " + mLyricsModel.duration) : "Invalid lyrics";
        Log.d(TAG, "lyricsSummary: " + lyricsSummary);
        final String finalDescription = "[" + ServiceManager.INSTANCE.getServiceType() + "]" + lyricsSummary + "\n";
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
            ServiceManager.INSTANCE.destroy();
            ServiceManager.INSTANCE.setServiceType(Objects.requireNonNull(ServiceType.Companion.fromType(serviceType)));
            ServiceManager.INSTANCE.initService(this.getApplicationContext(), this);
        }

        int lyricType = prefs.getInt(getString(R.string.prefs_key_lyric_type), LyricType.XML.ordinal());
        ServiceManager.INSTANCE.setLyricType(lyricType);

        boolean indicatorOn = prefs.getBoolean(getString(R.string.prefs_key_start_of_verse_indicator_switch), true);
        binding.lyricsView.enablePreludeEndPositionIndicator(indicatorOn);
        String indicatorColor = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_color), "Default");
        binding.lyricsView.setPreludeEndPositionIndicatorColor(Utils.colorInStringToDex(indicatorColor));
        String indicatorRadius = prefs.getString(getString(R.string.prefs_key_start_of_verse_indicator_radius), "6dp");
        binding.lyricsView.setPreludeEndPositionIndicatorRadius(Utils.dp2pix(this.getApplicationContext(), Float.parseFloat(indicatorRadius.replace("dp", ""))));
        int indicatorPaddingTop = prefs.getInt(getString(R.string.prefs_key_start_of_verse_indicator_padding_top), 6);
        binding.lyricsView.setPreludeEndPositionIndicatorPaddingTop(Utils.dp2pix(this.getApplicationContext(), indicatorPaddingTop));

        String defaultTextColor = prefs.getString(getString(R.string.prefs_key_normal_line_text_color), "Default");
        binding.lyricsView.setPreviousLineTextColor(Utils.colorInStringToDex(defaultTextColor));
        binding.lyricsView.setUpcomingLineTextColor(Utils.colorInStringToDex(defaultTextColor));

        int defaultTextSize = prefs.getInt(getString(R.string.prefs_key_normal_line_text_size), 13);
        binding.lyricsView.setTextSize(Utils.sp2pix(this.getApplicationContext(), defaultTextSize));

        String currentTextColor = prefs.getString(getString(R.string.prefs_key_current_line_text_color), "Yellow");
        binding.lyricsView.setCurrentLineTextColor(Utils.colorInStringToDex(currentTextColor));

        String highlightedTextColor = prefs.getString(getString(R.string.prefs_key_current_line_highlighted_text_color), "Red");
        binding.lyricsView.setCurrentLineHighlightedTextColor(Utils.colorInStringToDex(highlightedTextColor));

        int currentTextSize = prefs.getInt(getString(R.string.prefs_key_current_line_text_size), 24);
        binding.lyricsView.setCurrentLineTextSize(Utils.sp2pix(this.getApplicationContext(), currentTextSize));

        String lineSpacing = prefs.getString(getString(R.string.prefs_key_line_spacing), "6dp");
        binding.lyricsView.setLineSpacing(Utils.dp2pix(this.getApplicationContext(), Float.parseFloat(lineSpacing.replace("dp", ""))));

        boolean lyricsDraggingOn = prefs.getBoolean(getString(R.string.prefs_key_lyrics_dragging_switch), true);
        binding.lyricsView.enableDragging(lyricsDraggingOn);

        String labelWhenNoLyrics = prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text), getString(R.string.no_lyrics_label));
        binding.lyricsView.setLabelShownWhenNoLyrics(labelWhenNoLyrics);

        String labelWhenNoLyricsTextColor = prefs.getString(getString(R.string.prefs_key_lyrics_not_available_text_color), "Red");
        binding.lyricsView.setLabelShownWhenNoLyricsTextColor(Utils.colorInStringToDex(labelWhenNoLyricsTextColor));

        int labelWhenNoLyricsTextSize = prefs.getInt(getString(R.string.prefs_key_lyrics_not_available_text_size), 26);
        binding.lyricsView.setLabelShownWhenNoLyricsTextSize(Utils.sp2pix(this.getApplicationContext(), labelWhenNoLyricsTextSize));

        String heightOfRefPitch = prefs.getString(getString(R.string.prefs_key_ref_pitch_stick_height), "6dp");
        binding.scoringView.setRefPitchStickHeight(Utils.dp2pix(this.getApplicationContext(), Float.parseFloat(heightOfRefPitch.replace("dp", ""))));

        String defaultRefPitchStickColor = prefs.getString(getString(R.string.prefs_key_default_ref_pitch_stick_color), "Default");
        binding.scoringView.setRefPitchStickDefaultColor(Utils.colorInStringToDex(defaultRefPitchStickColor));

        String highlightedRefPitchStickColor = prefs.getString(getString(R.string.prefs_key_highlighted_ref_pitch_stick_color), "Default");
        binding.scoringView.setRefPitchStickHighlightedColor(Utils.colorInStringToDex(highlightedRefPitchStickColor));

        Drawable[] drawables = null;
        boolean customizedIndicatorAndParticleOn = prefs.getBoolean(getString(R.string.prefs_key_customized_indicator_and_particle_switch), false);
        if (customizedIndicatorAndParticleOn) {
            Bitmap bitmap = Utils.drawableToBitmap(getDrawable(R.drawable.pitch_indicator));
            binding.scoringView.setLocalPitchIndicator(bitmap);
            drawables = new Drawable[]{getDrawable(R.drawable.pitch_indicator), getDrawable(R.drawable.pitch_indicator_yellow), getDrawable(R.drawable.ic_launcher_background), getDrawable(R.drawable.star7), getDrawable(R.drawable.star8)};
        } else {
            binding.scoringView.setLocalPitchIndicator(null);
        }

        boolean particleEffectOn = prefs.getBoolean(getString(R.string.prefs_key_particle_effect_switch), true);
        binding.scoringView.enableParticleEffect(particleEffectOn, drawables);


        float particleHitOnThreshold = prefs.getFloat(getString(R.string.prefs_key_hit_score_threshold), 0.8f);
        binding.scoringView.setThresholdOfHitScore(particleHitOnThreshold);
    }

    private void switchMusic() {
        ServiceManager.INSTANCE.stop();
        ServiceManager.INSTANCE.switchMusic();
        doPlay();
    }


    private void updatePlayingProgress(final long progress) {
        binding.playingProgress.setText(String.valueOf(progress));
    }

    private void updateCallback(final String callbackMessage) {
        binding.callBack.setText(callbackMessage);
    }

    private void doPlay() {
        binding.play.setText(this.getResources().getString(R.string.stop));
        mLyricsCurrentProgress = 0;
        binding.playOriginal.setText(this.getResources().getString(R.string.play_accompany));
        ServiceManager.INSTANCE.play();
    }

    private void doStop() {
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
            mKaraokeView.setPitch(0, 0);
            mKaraokeView.reset();
            mKaraokeView = null;
        }

        ServiceManager.INSTANCE.destroy();
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
                updateCallback("Next");
                mLyricsCurrentProgress = 0; // Replay if already playing
                mState = PlayerState.PLAYING;
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
    public void onMusicLyricRequest(long songCode, @Nullable String lyricUrl, @Nullable String pitchUrl, int lyricOffset) {
        loadTheLyrics(lyricUrl, pitchUrl, lyricOffset);
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
        if (mState == PlayerState.PLAYING) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mKaraokeView.setPitch((float) speakerPitch, (int) progressInMs);
                }
            });
        }
    }

    @Override
    public void onMusicPlaying() {
        updateCallback("Playing");
        mState = PlayerState.PLAYING;
    }

    @Override
    public void onMusicStop() {
        updateCallback("Stop");
        mState = PlayerState.IDLE;
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