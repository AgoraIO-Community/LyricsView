# LyricsView for Android
Parsing and displaying lyrics dynamically

# 简介
Agora 歌词控件(LyricsView)支持在歌曲播放的同时同步显示 LRC 或 XML 格式的歌词。本文介绍如何在项目中集成并使用 Agora 歌词控件。

`功能描述`

歌曲播放时，根据当前播放进度显示对应的歌词

手势拖动到指定时间的歌词，歌曲进度随之改变

自定义歌词界面布局

自定义更换歌词背景

`实现方法`

## 引入 LyricsView 控件

### 源代码模式

参考如下步骤，在主播端和观众端添加 LyricsView 控件：

将该项目下的 `lrcview` 文件夹拷贝至你的项目文件夹下。

在你的项目中引入 `lrcview` 控件。

打开项目的 `settings.gradle` 文件，添加如下代码：
```
include ':lrcview'
```
在你的项目中添加 `lrcview` 控件的依赖项。打开项目的 `app/build.gradle` 文件，添加如下代码：
```
dependencies {
    ...
    implementation project(':lrcview')
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
    implementation 'com.github.AgoraIO-Community:LyricsView:1.0.25'
}
```

## 自定义 LyricsView 控件界面布局

在项目的 Activity 中，自定义 LyricsView 控件的界面布局。示例代码如下：
```
<io.agora.lyrics_view.LrcView
    android:id="@+id/lrc_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingStart="10dp"
    android:paddingTop="20dp"
    android:paddingEnd="10dp"
    android:paddingBottom="20dp"
    // 当前行歌词颜色
    app:lrcCurrentTextColor="@color/ktv_lrc_highlight"
    // 歌词行间距
    app:lrcDividerHeight="20dp"
    // 无歌词情况下的默认文字
    app:lrcLabel="暂无歌词"
    // 非当前行歌词颜色
    app:lrcNormalTextColor="@color/ktv_lrc_nomal"
    // 非当前行歌词字体大小
    app:lrcNormalTextSize="16sp"
    // 歌词对齐方式
    app:lrcTextGravity="center"
    // 当前行歌词字体大小
    app:lrcTextSize="26sp" />
```

## 声明和初始化 LyricsView 控件对象

在项目的 Activity 中，声明和初始化 LyricsView 控件对象。示例代码如下：
```
public class LiveActivity extends RtcBaseActivity {
    private LrcView mLrcView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ...
        mLrcView = findViewById(R.id.lrc_view);
        ...
    }
}
```

## 打分回调

```
public interface OnSingScoreListener {
    /**
     * 咪咕歌词原始参考 pitch 值回调, 用于开发者自行实现打分逻辑. 歌词每个 tone 回调一次
     *
     * @param pitch      当前 tone 的 pitch 值
     * @param totalCount 整个 xml 的 tone 个数, 用于开发者方便自己在 app 层计算平均分.
     */
    void onOriginalPitch(float pitch, int totalCount);

    /**
     * 歌词组件内置的打分回调, 每句歌词结束的时候提供回调(句指 xml 中的 sentence 节点),
     * 并提供 totalScore 参考值用于按照百分比方式显示分数
     *
     * @param score           这次回调的分数 0-10 之间
     * @param cumulativeScore 累计的分数 初始分累计到当前的分数
     * @param totalScore      总分 初始分(默认值 0 分) + xml 中 sentence 的个数 * 10
     */
    void onScore(double score, double cumulativeScore, double totalScore);
}
```

### 初始分默认为 0 分, 如果要重定义在下面:

```
<io.agora.lyrics_view.LrcView
    android:id="@+id/lrc_view"
    app:lrcDefaultScore="0"
    app:lrcEnableDrag="true"
    app:lrcScore="true"
 />
```

### 打分相关:

```
<io.agora.lyrics_view.PitchView
    // 设定最小得分的标准，百分制，默认为 40，即得分小于 40 的不参与最终算分，这样在舍弃一些低分之后整句会相对来说会比较容易得高分
    // 如果设定为 0，即所有得分都会参与最终算分，需要演唱者每个字都认真唱
    // 另外单字得分相关的两个参数 PitchView#mScoreLevel(默认为 0，取值为 0~100) 以及 PitchView#mCompensationOffset(默认为 0，取值为 -100~100)，如需修改请参照对应 API
    app:minimumScore="40"
    // 控制游标的半径
    app:pitchIndicatorRadius="6dp"
    // 初始得分，如不需要可设置为 0
    app:pitchInitialScore="0"
    // 控制基准音条的高度
    app:pitchStickHeight="6dp" />
```

核心 API 参考如下：

| API                                 | 实现功能                                                                                                                  |
|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| setSeekListener                     | 绑定歌词拖动事件回调，用于接收拖动事件中状态或者事件回调。                                                                                         |
| setSingScoreListener                | 绑定唱歌打分事件回调，用于接收唱歌过程中事件回调。                                                                                             |
| setEnableDrag                       | 设置是否允许上下拖动歌词。                                                                                                         |
| setTotalDuration                    | 设置歌词总时长，单位毫秒。必须与歌曲时长一致。                                                                                               |
| setNormalColor                      | 设置非当前行歌词字体颜色。                                                                                                         |
| setNormalTextSize                   | 普通歌词文本字体大小。                                                                                                           |
| setCurrentTextSize                  | 当前歌词文本字体大小。                                                                                                           |
| setCurrentColor                     | 设置当前行歌词的字体颜色。                                                                                                         |
| setLabel                            | 设置歌词为空时屏幕中央显示的文字，如“暂无歌词”。                                                                                             |
| setLrcData                          | 手动设置歌词数据。                                                                                                             |
| reset                               | 重置内部状态，清空已经加载的歌词。                                                                                                     |
| loadLrc(mainLrcText, secondLrcText) | 加载本地歌词文件。 支持加载 LRC 格式的双语歌词，mainLrcText 是中文歌词对象，secondLrcText 是英文歌词对象。对于非双语歌词， 将 mainLrcText 或 secondLrcText 设置为 null。 |
| onLoadLrcCompleted                  | 歌词文件加载完成回调。                                                                                                           |
| updateTime                          | 根据当前歌曲播放进度更新歌词进度，单位为毫秒。                                                                                               |
| hasLrc                              | 获取歌词文件状态。true：歌词有效 false：歌词无效，无法播放。                                                                                   |
