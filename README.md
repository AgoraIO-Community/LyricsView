# KaraokeView for Android

Build KTV app effortlessly with KaraokeView

# 简介

声网 KTV 控件(KaraokeView)支持在歌曲播放的同时同步显示歌词，支持演唱打分以及相关效果显示。本文介绍如何在项目中集成并使用
KaraokeView。

`注意：该版本版本同当前稳定版 2.x 在 API 上并不兼容1.x版本`

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
    implementation("io.github.winskyan:Agora-LyricsViewEx:2.0.0.130")
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

        // 2. Parse the lyrics and set up the lyrics model for KaraokeView
        mLyricsModel = KaraokeView.parseLyricData(lrc, pitch);
        if (mLyricsModel != null) {
            mKaraokeView.setLyricData(mLyricsModel);
        }

        // 3. Set up event callbacks
        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
            }
        });

        // 4. Drive the KaraokeView running based on the progress of media player
        mKaraokeView.setProgress(position);

        // 5. Reset
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

}

```

## 自定义 KaraokeView 控件

### 自定义 LyricsView 控件：

```xml

<io.agora.karaoke_view_ex.LyricsView android:id="@+id/lyrics_view"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:paddingStart="10dp" android:paddingTop="20dp" android:paddingEnd="10dp"
    android:paddingBottom="20dp"// 当前行歌词颜色app:currentTextColor="@color/ktv_lrc_current"// 当前行歌词字体大小app:currentLineTextSize="26sp"// 当前行歌词高亮颜色app:currentLineHighlightedTextColor="@color/ktv_lrc_highlight"// 歌词行间距app:lineSpacing="20dp"// 无歌词情况下的默认文字app:labelWhenNoLyrics="暂无歌词"// 已经唱过歌词颜色app:previousLineTextColor="@color/ktv_lrc_nomal"// 即将显示歌词颜色app:upcomingLineTextColor="@color/ktv_lrc_nomal"// 歌词字体大小app:textSize="16sp"// 歌词文字显示对齐方式app:textGravity="center" />
```

### 自定义 ScoringView 控件：

```xml

<io.agora.karaoke_view_ex.ScoringView android:id="@+id/scoring_view
    // 基准音条高度
    app:pitchStickHeight="4dp"// 基准音条高亮状态颜色app:pitchStickHighlightedColor="@color/pink_b4"/>
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
