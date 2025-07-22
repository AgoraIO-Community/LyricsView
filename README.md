# KaraokeView for Android

> Build KTV app effortlessly with KaraokeView

## 目录

- [简介](#简介)
- [功能特点](#功能特点)
- [运行示例](#运行示例)
- [集成方式](#集成方式)
  - [Maven 集成](#方式一maven-集成)
  - [源代码集成](#方式二源代码集成)
- [使用指南](#使用指南)
  - [初始化与基本使用](#初始化与基本使用)
  - [事件回调接口](#事件回调接口)
- [自定义配置](#自定义配置)
  - [LyricsView 自定义属性](#lyricsview-自定义属性)
  - [ScoringView 自定义属性](#scoringview-自定义属性)
  - [自定义粒子动画效果](#自定义粒子动画效果)
  - [自定义打分算法](#自定义打分算法)

## 简介

声网 KTV 控件(KaraokeView)是一个专为 Android
平台设计的歌词同步显示和演唱打分组件。它支持在歌曲播放的同时同步显示歌词，支持演唱打分以及相关效果显示，为开发者提供了构建
KTV 应用的完整解决方案。

> **注意**：该版本稳定版 2.x 在 API 上并不兼容1.x版本，但2.1.x版本后兼容1.x版本的所有功能，建议升级到2.1.x版本

## 功能特点

✨ **核心功能**

- 歌曲播放时，根据当前播放进度显示对应的歌词
- 手势拖动到指定时间的歌词，歌曲进度随之改变
- 根据演唱结果进行打分以及显示对应的视图效果

🎨 **自定义选项**

- 自定义歌词界面布局和字体样式
- 自定义更换歌词背景
- 自定义粒子动画效果
- 自定义打分算法

## 运行示例

1. 在项目根目录下编辑 `local.properties` 文件，添加以下配置参数：

```properties
# 声网配置
APP_CERTIFICATE=XXX  # 声网证书
APP_ID=XXX           # 声网 App ID
# 音速达曲库配置
YSD_APP_ID=XXX       # 音速达曲库 App ID
YSD_APP_KEY=XXX      # 音速达曲库 App Key
YSD_TOKEN_HOST=XXX   # 音速达曲库 Token 获取地址
```

2. 使用 Android Studio 打开项目，等待依赖同步完成

3. 点击工具栏中的"运行"按钮（绿色三角形）或使用快捷键 `Shift+F10` 即可启动示例应用

## 集成方式

### 方式一：Maven 集成

在项目的 `build.gradle` 文件中添加依赖：

```gradle
dependencies {
    implementation("io.github.winskyan:Agora-LyricsViewEx:2.2.1")
}
```

### 方式二：源代码集成

1. 将该项目下的 `karaoke_view_ex` 文件夹拷贝至你的项目文件夹下

2. 在项目的 `settings.gradle` 文件中添加：

```gradle
include ':karaoke_view_ex'
```

3. 在应用模块的 `build.gradle` 文件中添加依赖：

```gradle
dependencies {
    implementation project(':karaoke_view_ex')
}
```

## 使用指南

### 初始化与基本使用

在项目的 Activity 中声明和初始化 KaraokeView 控件：

```java
public class LiveActivity extends RtcBaseActivity {
    private KaraokeView mKaraokeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. 初始化KaraokeView
        mKaraokeView = new KaraokeView(
                binding.enableLyrics.isChecked() ? binding.lyricsView : null,
                binding.enableScoring.isChecked() ? binding.scoringView : null
        );

        // 2. 设置事件回调
        mKaraokeView.setKaraokeEvent(new KaraokeEvent() {
            @Override
            public void onDragTo(KaraokeView view, long position) {
                mKaraokeView.setProgress(position);
            }

            @Override
            public void onLineFinished(KaraokeView view, LyricsLineModel line, int score,
                                       int cumulativeScore, int index, int lineCount) {
                // 启用内部打分时，每行歌词结束会触发此回调
            }
        });

        // 3. 解析歌词并设置歌词模型
        // pitch文件可选，如无则传null
        mLyricsModel = KaraokeView.parseLyricData(lrc, pitch);
        if (mLyricsModel != null) {
            // 设置歌词数据并指定是否启用内部打分
            // 启用内部打分时，每行歌词结束会触发onLineFinished回调
            mKaraokeView.setLyricData(mLyricsModel, true);
        }

        // 4. 根据媒体播放器进度驱动KaraokeView
        mKaraokeView.setProgress(position);

        // 5. 设置演唱者音高和媒体播放器进度
        mKaraokeView.setPitch((float) speakerPitch, (int) progressInMs);

        // 6. 重置控件
        mKaraokeView.reset();
    }
}
```

### 事件回调接口

```java
public interface KaraokeEvent {
    /**
     * 控件歌词部分拖动交互回调
     * 拖动之后回调歌词当前位置，用于驱动播放器调整播放位置
     *
     * @param view     当前组件对象
     * @param position 拖动之后的歌词当前位置
     */
    void onDragTo(KaraokeView view, long position);

    /**
     * 歌词组件内置的打分回调
     * 每句歌词结束时提供回调(句指XML中的sentence节点)
     * 并提供totalScore参考值用于按照百分比方式显示分数
     *
     * @param view            当前组件对象
     * @param line            当前句歌词模型对象
     * @param score           当前句得分
     * @param cumulativeScore 累计的分数（初始分累计到当前的分数）
     * @param index           当前句索引
     * @param lineCount       整个歌词总句数
     */
    void onLineFinished(KaraokeView view, LyricsLineModel line, int score,
                        int cumulativeScore, int index, int lineCount);
}
```

## 自定义配置

### LyricsView 自定义属性

在XML布局文件中定义LyricsView：

```xml

<io.agora.karaoke_view_ex.LyricsView android:id="@+id/lyrics_view"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:paddingStart="10dp" android:paddingTop="20dp" android:paddingEnd="10dp"

    app:currentLineTextColor="@color/ktv_lrc_current"           <!-- 当前行歌词颜色 -->
    app:currentLineTextSize="26sp"                              <!-- 当前行歌词字体大小 -->
    app:currentLineHighlightedTextColor="@color/ktv_lrc_highlight" <!-- 当前行歌词高亮颜色 -->
    app:lineSpacing="20dp"                                      <!-- 歌词行间距 -->
    app:labelWhenNoLyrics="暂无歌词"                            <!-- 无歌词情况下的默认文字 -->
    app:previousLineTextColor="@color/ktv_lrc_nomal"            <!-- 已唱过歌词颜色 -->
    app:upcomingLineTextColor="@color/ktv_lrc_nomal"            <!-- 即将显示歌词颜色 -->
    app:textSize="16sp"                                         <!-- 歌词字体大小 -->
    app:textGravity="center"                                    <!-- 歌词文字显示对齐方式 -->
    app:enableLineWrap="false" />                               <!-- 超长歌词是否换行显示 -->
```

### ScoringView 自定义属性

在XML布局文件中定义ScoringView：

```xml

<io.agora.karaoke_view_ex.ScoringView android:id="@+id/scoring_view"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    app:pitchStickHeight="4dp"                                 <!-- 基准音条高度 -->
    app:pitchStickHighlightedColor="@color/pink_b4" />         <!-- 基准音条高亮状态颜色 -->
```

### 自定义粒子动画效果

继承ScoringView并重写粒子系统初始化方法：

```java
public class MyScoringView extends ScoringView {
    @Override
    public void initParticleSystem(Drawable[] particles) {
        // 使用Leonids库时需添加依赖
        // api 'com.github.guohai:Leonids:9f5a9190f6'

        mParticlesPerSecond = 16;
        particles = {..., ..., ...}  // 可选

        mParticleSystem = new ParticleSystem((ViewGroup) this.getParent(),
                particles.length * 6, particles, 900);
        mParticleSystem.setRotationSpeedRange(90, 180)
                .setScaleRange(0.7f, 1.6f)
                .setSpeedModuleAndAngleRange(0.10f, 0.20f, 120, 240)
                .setFadeOut(300, new AccelerateInterpolator());
    }
}
```

### 自定义打分算法

实现IScoringAlgorithm接口：

```java
public class MyScoringAlgorithm implements IScoringAlgorithm {
    public MyScoringAlgorithm() {
    }

    @Override
    public float getPitchScore(float currentPitch, float currentRefPitch) {
        final float scoreAfterNormalization = ScoringMachine.calculateScore2(
                0, mScoringLevel, mScoringCompensationOffset, currentPitch, currentRefPitch);
        // 返回的为 [0, 1] 之间的规范值
        return scoreAfterNormalization;
    }

    @Override
    public int getLineScore(final LinkedHashMap<Long, Float> pitchesForLine,
                            final int indexOfLineJustFinished,
                            final LyricsLineModel lineJustFinished) {
        // 自定义每行得分计算逻辑
        int scoreThisLine = ...;
        return scoreThisLine;
    }
}
```

## 更新日志

### [2.2.1] - 2025-07-22

#### 优化

- 支持 16K page size，提升大文件歌词的兼容性和加载性能。

---

### [2.2.0] - 2025-03-21

#### 新增

- `LyricsView` 新增属性 `app:enableLineWrap`，可控制超长歌词是否自动换行显示。

---

### [2.1.2] - 2025-01-07

#### 修复

- 修复歌词组件在特定场景下异常崩溃的问题。
- 修复打分功能在部分歌词下分数异常的问题。

---

### [2.1.0] - 2024-08-06

#### 新增

- 兼容音速达和音集协曲库，支持更多曲库格式。

---

> 注：2.x 版本在 API 上与 1.x 不兼容，2.1.x 版本后兼容 1.x 的全部功能，建议升级至最新版本。
