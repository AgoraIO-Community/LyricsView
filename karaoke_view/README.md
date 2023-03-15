# KaraokeView for Android
Build KTV app effortlessly with KaraokeView

# 简介
声网 KTV 控件(KaraokeView)支持在歌曲播放的同时同步显示歌词，支持演唱打分以及相关效果显示。本文介绍如何在项目中集成并使用 KaraokeView。

`注意：该版本版本同当前稳定版 1.0.x 在 API 上并不兼容，目前处于 beta 状态，请根据需要评估之后决定是否需要升级，我们会在版本稳定之后更新这段文字`

`功能描述`

歌曲播放时，根据当前播放进度显示对应的歌词

手势拖动到指定时间的歌词，歌曲进度随之改变

自定义歌词界面布局

自定义更换歌词背景

根据演唱结果进行打分以及显示对应的视图效果

`实现方法`

## 引入 KaraokeView 控件

### 源代码模式

参考如下步骤，在主播端和观众端添加 KaraokeView 控件：

将该项目下的 `karaoke_view` 文件夹拷贝至你的项目文件夹下。

在你的项目中引入 `karaoke_view` 控件。

打开项目的 `settings.gradle` 文件，添加如下代码：
```
include ':karaoke_view'
```
在你的项目中添加 `karaoke_view` 控件的依赖项。打开项目的 `app/build.gradle` 文件，添加如下代码：
```
dependencies {
    ...
    implementation project(':karaoke_view')
}
```

### JitPack 方式

```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

```
dependencies {
    ...
    implementation 'com.github.AgoraIO-Community:LyricsView:1.1.1-beta.2'
}
```

## 自定义 LyricsView 控件界面布局

在项目的 Activity 中，自定义 LyricsView 控件的界面布局。示例代码如下：
```xml
<io.agora.karaoke_view.v11.LyricsView
    android:id="@+id/lyrics_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingStart="10dp"
    android:paddingTop="20dp"
    android:paddingEnd="10dp"
    android:paddingBottom="20dp"
    // 当前行歌词颜色
    app:currentTextColor="@color/ktv_lrc_current"
    // 当前行歌词字体大小
    app:currentTextSize="26sp"
    // 当前行歌词高亮颜色
    app:currentHighlightedTextColor="@color/ktv_lrc_highlight"
    // 歌词行间距
    app:lineSpacing="20dp"
    // 无歌词情况下的默认文字
    app:labelWhenNoLyrics="暂无歌词"
    // 已经唱过歌词颜色
    app:pastTextColor="@color/ktv_lrc_nomal"
    // 即将显示的歌词颜色
    app:upcomingTextColor="@color/ktv_lrc_nomal"
    // 默认歌词字体大小
    app:defaultTextSize="16sp"
    // 歌词文字显示对齐方式
    app:textGravity="center" />
```

## 声明和初始化 LyricsView/ScoringView 控件对象

在项目的 Activity 中，声明和初始化 LyricsView/ScoringView 等控件对象。示例代码如下：
```Java
public class LiveActivity extends RtcBaseActivity {
    private LyricsView mLyricsView;
    private ScoringView mScoringView;

    private KaraokeView mKaraokeView;

    private MockRtcEngineWithPlayer mMockRtcWithPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ...
        mLyricsView = findViewById(R.id.lyrics_view);
        mScoringView = findViewById(R.id.scoring_view);

        mKaraokeView = new KaraokeView(mLyricsView, mScoringView);

        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
            }

            @Override
            public void onRefPitchUpdate(float refPitch, int numberOfRefPitches) {
                ...
            }

            @Override
            public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulatedScore, int index, int total) {
                ...
            }
        });

        mMockRtcWithPlayer = new MockRtcEngineWithPlayer(new PlayerEvent() {
            @Override
            public void onPlayPosition(long position) {
                // This will trigger KaraokeView running, must be stable and smooth
                mKaraokeView.setProgress(position);
            }

            @Override
            public void onLocalPitch(int pitch) {
                // This will trigger ScoringMachine and all the effects running
                mKaraokeView.setPitch(pitch);
            }
        });

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
     * 歌词原始参考 pitch 值回调, 用于开发者自行实现打分逻辑。 歌词每个 tone 回调一次
     *
     * @param refPitch           当前 tone 的 pitch 值
     * @param numberOfRefPitches 整个歌词当中的 tone 个数, 用于开发者方便自己在 app 层计算平均分.
     */
    public void onRefPitchUpdate(float refPitch, int numberOfRefPitches);

    /**
     * 歌词组件内置的打分回调, 每句歌词结束的时候提供回调(句指 xml 中的 sentence 节点),
     * 并提供 totalScore 参考值用于按照百分比方式显示分数
     *
     * @param view            当前组件对象
     * @param line            当前句歌词模型对象
     * @param score           当前句得分
     * @param cumulativeScore 累计的分数 初始分累计到当前的分数
     * @param index           当前句索引
     * @param total           整个歌词总分
     */
    public void onLineFinished(KaraokeView view, LyricsLineModel line, int score, int cumulativeScore, int index, int total);
}

```

### 初始分默认为 0 分, 如果要重定义在下面:

```xml
<io.agora.karaoke_view.v11.ScoringView
    android:id="@+id/scoring_view
    app:pitchInitialScore="0"
  />
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
public class DefaultScoringAlgorithm implements IScoringAlgorithm {
    // Maximum score for one line, 100 for maximum and 0 for minimum
    private final int mMaximumScoreForLine = 100;

    private ScoringMachine.OnScoringListener mListener;

    // Indicating the difficulty in scoring(can change by app)
    private int mScoringLevel = 10; // 0~100
    private int mScoringCompensationOffset = 0; // -100~100

    public DefaultScoringAlgorithm() {
    }

    @Override
    public float pitchToScore(float currentPitch, float currentRefPitch) {
        final float scoreAfterNormalization = ScoringMachine.calculateScore2(0, mScoringLevel, mScoringCompensationOffset, currentPitch, currentRefPitch);
        // 返回的为 [0, 1] 之间的规范值
        return scoreAfterNormalization;
    }

    @Override
    public int calcLineScore(final LinkedHashMap<Long, Float> pitchesForLine, final int indexOfLineJustFinished, final LyricsLineModel lineJustFinished) {
        // 计算歌词当前句的分数 = 单字打分总和 / 分数个数
        // 其中单字打分 = pitchToScore(...) * mMaximumScoreForLine
        float totalScoreForThisLine = 0;
        int scoreCount = 0;

        Float scoreForOnePitch;
        Iterator<Long> iterator = pitchesForLine.keySet().iterator();

        while (iterator.hasNext()) {
            Long myKeyTimestamp = iterator.next();
            if (myKeyTimestamp <= lineJustFinished.getEndTime()) { // 如果时间戳在需要打分的行范围内，就参与打分
                scoreForOnePitch = pitchesForLine.get(myKeyTimestamp);

                iterator.remove();
                pitchesForLine.remove(myKeyTimestamp);

                if (scoreForOnePitch != null) {
                    totalScoreForThisLine += scoreForOnePitch; // 计算当前行总分
                }
                scoreCount++;
            }
        }

        scoreCount = Math.max(1, scoreCount);

        int scoreThisLine = (int) totalScoreForThisLine / scoreCount;

        return scoreThisLine;
    }
}
```


核心 API 参考如下：

| API                            | 实现功能                      |
|--------------------------------|---------------------------|
| setKaraokeEvent                | 绑定唱歌过程中拖动/打分/原始参考值等事件回调。  |
| enableDragging                 | 设置是否允许上下拖动歌词。             |
| setDefaultTextSize             | 设置歌词文本字体大小。               |
| setCurrentTextSize             | 设置当前歌词文本字体大小。             |
| setCurrentTextColor            | 设置当前行歌词文本字体颜色。            |
| setCurrentHighlightedTextColor | 设置当前行高亮歌词文本字体颜色。          |
| setPastTextColor               | 设置已经过去歌词字体颜色。             |
| setUpcomingTextColor           | 设置即将到来歌词字体颜色。             |
| labelWhenNoLyrics              | 设置歌词为空时屏幕中央显示的文字，如“暂无歌词”。 |
| reset                          | 重置内部状态，清空已经加载的歌词。         |
| setLyricsData                  | 手动设置歌词数据。                 |
| parseLyricsData                | 加载本地歌词文件。                 |
| setProgress                    | 根据当前歌曲播放进度更新歌词进度，单位为毫秒。   |
| setPitch                       | 根据演唱者 pitch 更新进行打分。       |