# KaraokeView for Android

Build KTV app effortlessly with KaraokeView

# 简介

声网 KTV 控件(KaraokeView)支持在歌曲播放的同时同步显示歌词，支持演唱打分以及相关效果显示。本文介绍如何在项目中集成并使用 KaraokeView。

`注意：该版本稳定版 2.x 在 API 上并不兼容1.x版本,但2.1.x版本后兼容1.x版本的所有功能，建议升级到2.1.x版本`

`功能描述`

歌曲播放时，根据当前播放进度显示对应的歌词

手势拖动到指定时间的歌词，歌曲进度随之改变

自定义歌词界面布局

自定义更换歌词背景

根据演唱结果进行打分以及显示对应的视图效果

## 集成 KaraokeView 控件

### Maven 方式

```
dependencies {
    ...
    implementation("io.github.winskyan:Agora-LyricsViewEx:2.1.0-alpha.4")
}
```

### 源代码模式

参考如下步骤，在主播端和观众端添加 KaraokeView 控件：

将该项目下的 `karaoke_view_ex` 文件夹拷贝至你的项目文件夹下。

在你的项目中引入 `karaoke_view_ex` 控件。

打开项目的 `settings.gradle` 文件，添加如下代码：

```
include ':karaoke_view_ex'
```

在你的项目中添加 `karaoke_view_ex` 控件的依赖项。打开项目的 `app/build.gradle` 文件，添加如下代码：

```
dependencies {
    ...
    implementation project(':karaoke_view_ex')
}
```

## 声明和初始化 KaraokeView/LyricsView/ScoringView 控件对象

在项目的 Activity 中，声明和初始化 KaraokeView/LyricsView/ScoringView 等控件对象。示例代码如下：

```Java
public class LiveActivity extends RtcBaseActivity {
    private KaraokeView mKaraokeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ...

        // 1. Initialize
        mKaraokeView = new KaraokeView(binding.enableLyrics.isChecked() ? binding.lyricsView : null, binding.enableScoring.isChecked() ? binding.scoringView : null);

        // 2. Set up event callbacks
        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
            }

            @Override
            public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount) {
                //if enable internal scoring, the callback will be triggered when a line of lyrics is finished
            }
        });

        // 3. Parse the lyrics and set up the lyrics model for KaraokeView
        //pitch file is optional, if you don't have pitch file, just pass null
        mLyricsModel = KaraokeView.parseLyricData(lrc, pitch);
        if (mLyricsModel != null) {
            // Set the lyrics data to KaraokeView with whether to enable internal scoring
            // if enable internal scoring, the onLineFinished callback will be triggered when a line of lyrics is finished
            // if disable internal scoring, the onLineFinished callback will not be triggered
            mKaraokeView.setLyricData(mLyricsModel, true);
        }

        // 4. Drive the KaraokeView running based on the progress of media player
        mKaraokeView.setProgress(position);

        // 5. set the pitch of the speaker and the progress of the media player
        mKaraokeView.setPitch((float) speakerPitch, (int) progressInMs);

        // 6. Reset
        mKaraokeView.reset();
        ...
    }
}
```

## 控件相关事件回调

```Java
public interface KaraokeEvent {
    /**
     * 控件歌词部分拖动交互，拖动之后回调歌词当前位置，用于驱动播放器调整播放位置
     *
     * @param view     当前组件对象
     * @param position 拖动之后的歌词当前位置
     */
    public void onDragTo(KaraokeView view, long position);


    /**
     * 歌词组件内置的打分回调, 每句歌词结束的时候提供回调(句指 xml 中的 sentence 节点),
     * 并提供 totalScore 参考值用于按照百分比方式显示分数
     *
     * @param view            当前组件对象
     * @param line            当前句歌词模型对象
     * @param score           当前句得分
     * @param cumulativeScore 累计的分数 初始分累计到当前的分数
     * @param index           当前句索引
     * @param lineCount       整个歌词总句数
     */
    void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int lineCount);

}

```

## 自定义 KaraokeView 控件

### 自定义 LyricsView 控件：

```xml

<io.agora.karaoke_view_ex.LyricsView 
    android:id="@+id/lyrics_view"
    android:layout_width="match_parent" 
    android:layout_height="match_parent"
    android:paddingStart="10dp" 
    android:paddingTop="20dp" 
    android:paddingEnd="10dp"
    // 当前行歌词颜色
    app:currentTextColor="@color/ktv_lrc_current"
    // 当前行歌词字体大小
    app:currentLineTextSize="26sp"
    // 当前行歌词高亮颜色
    app:currentLineHighlightedTextColor="@color/ktv_lrc_highlight"
    // 歌词行间距
    app:lineSpacing="20dp"
    // 无歌词情况下的默认文字
    app:labelWhenNoLyrics="暂无歌词"
    // 已经唱过歌词颜色
    app:previousLineTextColor="@color/ktv_lrc_nomal"
    // 即将显示歌词颜色
    app:upcomingLineTextColor="@color/ktv_lrc_nomal"
    // 歌词字体大小
    app:textSize="16sp"
    // 歌词文字显示对齐方式
    app:textGravity="center" />
```

### 自定义 ScoringView 控件：

```xml

<io.agora.karaoke_view_ex.ScoringView
    android:id="@+id/scoring_view
    // 基准音条高度
    app:pitchStickHeight="4dp"
    // 基准音条高亮状态颜色
    app:pitchStickHighlightedColor="@color/pink_b4"
```

### 重写粒子动画效果:

```Java
public class MyScoringView extends ScoringView {

    ...

    @Override
    public void initParticleSystem(Drawable[] particles) {
        // Import Leonids as below when re-write initParticleSystem
        // api 'com.github.guohai:Leonids:9f5a9190f6'
        mParticlesPerSecond = 16;
        particles = {..., ..., ...} // Optional

        mParticleSystem = new ParticleSystem((ViewGroup) this.getParent(), particles.length * 6, particles, 900);
        mParticleSystem.setRotationSpeedRange(90, 180).setScaleRange(0.7f, 1.6f)
                .setSpeedModuleAndAngleRange(0.10f, 0.20f, 120, 240)
                .setFadeOut(300, new AccelerateInterpolator());
    }
}
```

### 重写打分逻辑:
```Java
public class MyScoringAlgorithm implements IScoringAlgorithm {
    public MyScoringAlgorithm() {
    }

    @Override
    public float getPitchScore(float currentPitch, float currentRefPitch) {
        final float scoreAfterNormalization = ScoringMachine.calculateScore2(0, mScoringLevel, mScoringCompensationOffset, currentPitch, currentRefPitch);
        // 返回的为 [0, 1] 之间的规范值
        return scoreAfterNormalization;
    }

    @Override
    public int getLineScore(final LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished) {
        ...
        scoreThisLine = ...
        ...
        return scoreThisLine;
    }
}
```

