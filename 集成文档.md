# 声网 RTC + MCC + KaraokeView 集成文档

本指南详细介绍如何在 Android 项目中集成声网 RTC SDK、音乐内容中心（MCC）以及 KaraokeView 歌词组件，实现多曲库（音集协、音速达）K歌、歌词显示与打分等功能。

---

## 一、RTC SDK（RtcEngine）集成与使用

### 1. 初始化 RtcEngine

```kotlin
val rtcEngineConfig = RtcEngineConfig()
rtcEngineConfig.mContext = applicationContext
rtcEngineConfig.mAppId = "你的声网AppId"
rtcEngineConfig.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
rtcEngineConfig.mEventHandler = object : IRtcEngineEventHandler() {
    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
        // 加入频道成功
    }
    override fun onLeaveChannel(stats: RtcStats) {
        // 离开频道
    }
}
rtcEngineConfig.mAudioScenario = Constants.AUDIO_SCENARIO_CHORUS
val rtcEngine = RtcEngine.create(rtcEngineConfig)
```

### 2. 加入频道

```kotlin
val channelId = "your_channel_id"
val rtcToken = "your_rtc_token"
val userUid = 123456
rtcEngine.joinChannel(rtcToken, channelId, userUid, ChannelMediaOptions().apply {
    publishMicrophoneTrack = true
    autoSubscribeAudio = true
    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
})
```

### 3. 离开频道与销毁

```kotlin
rtcEngine.leaveChannel()
RtcEngine.destroy()
```

---

## 二、音乐内容中心（IAgoraMusicContentCenter）集成与使用

### 1. 创建 MCC 实例

```kotlin
val mcc = IAgoraMusicContentCenter.create(rtcEngine)
```

### 2. 初始化 MCC

```kotlin
val mccConfig = MusicContentCenterConfiguration()
mccConfig.eventHandler = object : IMusicContentCenterEventHandler {
    // 处理预加载、歌词等回调
}
mccConfig.scoreEventHandler = object : IScoreEventHandler {
    // 处理实时音高、句分等回调
}
mcc.initialize(mccConfig)
```

### 3. 添加曲库（Vendor）

#### 音集协（Vendor 1）

```kotlin
val vendor1Params = JSONObject().apply {
    put("appId", "你的AppId")
    put("token", "RTM Token")
    put("userId", "用户ID")
    put("channelId", channelId)
    put("channelUserId", userUid.toString())
}
mcc.addVendor(MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT, vendor1Params.toString())
```

#### 音速达（Vendor 2）

```kotlin
val vendor2Params = JSONObject().apply {
    put("appId", "音速达AppId")
    put("appKey", "音速达AppKey")
    put("token", "音速达Token")
    put("userId", "音速达UserId")
    put("deviceId", "设备唯一ID")
    put("channelId", channelId)
    put("channelUserId", userUid.toString())
}
mcc.addVendor(MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_2, vendor2Params.toString())
```

### 4. 创建音乐播放器

```kotlin
val musicPlayer = mcc.createMusicPlayer()
musicPlayer.registerPlayerObserver(object : IMediaPlayerObserver {
    override fun onPlayerStateChanged(state: Constants.MediaPlayerState, reason: Constants.MediaPlayerReason) {
        // 监听播放状态
    }
    // 其他回调
})
```

### 5. 预加载歌曲与歌词

```kotlin
val vendorId = MccConstants.MusicContentCenterVendorId.MUSIC_CONTENT_CENTER_VENDOR_DEFAULT // 或 VENDOR_2
val songCode = "歌曲ID"
val lyricType = MccConstants.LyricSourceType.LYRIC_SOURCE_TYPE_XML // 或 LRC
val songOptionJson = "{}" // 视需求而定
val internalSongCode = mcc.getInternalSongCode(vendorId, songCode, songOptionJson)
mcc.preload(internalSongCode)
```

**回调处理**  
在 `IMusicContentCenterEventHandler.onPreLoadEvent` 或 `onLyricResult` 回调中，解析 payload，获取歌词和音高文件路径及相关参数：

```kotlin
// payload 示例：
// {"lyricPath":"/data/.../xxx.krc","pitchPath":"/data/.../xxx.pitch","songOffsetBegin":0,"songOffsetEnd":191000,"lyricOffset":2}
val json = JSONObject(payload)
val lyricPath = json.getString("lyricPath")           // 歌词文件本地路径
val pitchPath = json.getString("pitchPath")           // 音高文件本地路径
val songOffsetBegin = json.optInt("songOffsetBegin")  // 歌曲起始偏移（毫秒）
val songOffsetEnd = json.optInt("songOffsetEnd")      // 歌曲结束偏移（毫秒）
val lyricOffset = json.optInt("lyricOffset")          // 歌词偏移（毫秒）
```

- `lyricPath`：歌词文件的本地绝对路径（如.krc、.lrc、.xml）。
- `pitchPath`：音高文件的本地绝对路径（如.pitch）。
- `songOffsetBegin`：歌曲起始偏移（单位：毫秒），用于歌词/音频同步。
- `songOffsetEnd`：歌曲结束偏移（单位：毫秒），用于歌词/音频同步。
- `lyricOffset`：歌词整体偏移（单位：毫秒），用于歌词与音频的精确对齐。

这些参数需在加载歌词和同步进度时正确处理。

### 6. 播放歌曲

```kotlin
musicPlayer.open(internalSongCode, 0L) // 0L为起始播放位置
// 等待 onPlayerStateChanged 回调到 PLAYER_STATE_OPEN_COMPLETED 后
musicPlayer.play()
```

### 7. 暂停、恢复、停止、Seek

```kotlin
musicPlayer.pause()
musicPlayer.resume()
musicPlayer.stop()
musicPlayer.seek(50000L) // 跳转到50秒
```

### 8. 启动打分

```kotlin
mcc.startScore(internalSongCode)
```

启动打分后，SDK 会通过回调返回打分引擎启动结果。你可以在 IMusicContentCenterEventHandler 的 onStartScoreResult 回调中处理：

```kotlin
override fun onStartScoreResult(
    internalSongCode: Long,
    state: Int,   // 启动状态
    reason: Int   // 结果原因
) {
    // 常见判断方式：
    if (state == MccConstants.MusicContentCenterState.MUSIC_CONTENT_CENTER_STATE_START_SCORE_COMPLETED.value() &&
        reason == MccConstants.MusicContentCenterStateReason.MUSIC_CONTENT_CENTER_STATE_REASON_OK.value()) {
        // 启动打分成功，可进行后续操作（如自动播放、设置分数等级等）
    } else {
        // 启动失败，可根据 reason 处理异常
    }
}
```

- `state`：打分引擎启动状态，常用值：
  - `MUSIC_CONTENT_CENTER_STATE_START_SCORE_COMPLETED`（启动完成）
- `reason`：启动结果原因，常用值：
  - `MUSIC_CONTENT_CENTER_STATE_REASON_OK`（成功）
  - 其他值表示失败原因

建议在启动成功后再进行后续播放、分数等级设置等操作。

### 9. 设置打分等级

你可以通过 setScoreLevel 接口设置打分难度等级，建议在打分引擎启动成功后调用：

```kotlin
mcc.setScoreLevel(MccConstants.ScoreLevel.SCORE_LEVEL_5)
```

ScoreLevel 枚举说明（值越小难度越高）：

```java
public static enum ScoreLevel {
    SCORE_LEVEL_1(1), // 难度最高
    SCORE_LEVEL_2(2),
    SCORE_LEVEL_3(3),
    SCORE_LEVEL_4(4),
    SCORE_LEVEL_5(5); // 难度最低
}
```

- `SCORE_LEVEL_1`：难度最高，评分最严格
- `SCORE_LEVEL_5`：难度最低，评分最宽松

根据业务需求选择合适的打分等级。

### 10. 监听打分与实时音高回调

启动打分后，SDK 会通过回调返回每句歌词的分数、累计分数以及实时音高数据。你可以在 IScoreEventHandler 或自定义回调中监听：

#### 监听每句分数（打分结果）

```kotlin
// IScoreEventHandler 回调
override fun onLineScore(
    internalSongCode: Long,         // 歌曲内部ID
    lineScoreData: LineScoreData    // 当前句分数数据
) {
    val pitchScore = lineScoreData.pitchScore                 // 当前句分数
    val cumulativePitchScore = lineScoreData.cumulativePitchScore // 累计分数
    val index = lineScoreData.index                           // 当前句索引
    val totalLines = lineScoreData.totalLines                 // 总句数
    // 可用于更新分数UI
}
```

或参考 MainActivity/IMccCallback：

```kotlin
override fun onMusicLineScore(
    internalSongCode: Long,
    linePitchScore: Float,              // 当前句分数
    cumulativeTotalLinePitchScores: Float, // 累计分数
    performedLineIndex: Int,            // 当前句索引
    performedTotalLines: Int            // 总句数
) {
    // 可用于更新分数UI
}
```

- `internalSongCode`：MCC内部歌曲唯一ID。
- `pitchScore`/`linePitchScore`：当前句分数。
- `cumulativePitchScore`/`cumulativeTotalLinePitchScores`：累计分数。
- `index`/`performedLineIndex`：当前句索引。
- `totalLines`/`performedTotalLines`：总句数。

#### 监听实时音高数据

```kotlin
// IScoreEventHandler 回调
override fun onPitch(
    internalSongCode: Long,
    rawScoreData: RawScoreData
) {
    val speakerPitch = rawScoreData.speakerPitch   // 用户当前音高
    val pitchScore = rawScoreData.pitchScore       // 当前音高分数
    val progressInMs = rawScoreData.progressInMs   // 当前播放进度（毫秒）
    // 可用于KaraokeView实时绘制
}
```

或参考 MainActivity/IMccCallback：

```kotlin
override fun onMusicPitchScore(
    internalSongCode: Long,
    voicePitch: Double,       // 用户当前音高
    pitchScore: Double,       // 当前音高分数
    progressInMs: Long        // 当前播放进度（毫秒）
) {
    if (MusicManager.isPlaying()) {
        runOnUiThread {
            karaokeView.setPitch(
                voicePitch.toFloat(),
                pitchScore.toFloat(),
                progressInMs.toInt()
            )
        }
    }
}
```

- `voicePitch`/`speakerPitch`：用户当前音高。
- `pitchScore`：当前音高分数。
- `progressInMs`：当前播放进度（毫秒）。

你可以在这些回调中实时更新分数显示、进度、音高曲线等UI。

### 11. 销毁 MCC

```kotlin
musicPlayer.unRegisterPlayerObserver(observer)
mcc.unregisterEventHandler(eventHandler)
mcc.unregisterScoreEventHandler(scoreEventHandler)
mcc.destroyMusicPlayer(musicPlayer)
IAgoraMusicContentCenter.destroy()
```

---

## 三、KaraokeView 歌词组件集成与使用

### 1. 初始化KaraokeView

KaraokeView 通常与歌词视图（LyricsView）和打分视图（ScoringView）配合使用。初始化时可以根据需求选择是否启用歌词和打分：

```kotlin
val karaokeView = KaraokeView(
    if (enableLyrics) lyricsView else null,
    if (enableScoring) scoringView else null
)
```

### 2. 设置KaraokeEvent监听

KaraokeView 支持设置事件监听器，用于处理拖动、每句分数等事件。参考 MainActivity：

```kotlin
karaokeView.setKaraokeEvent(object : KaraokeEvent {
    override fun onDragTo(view: KaraokeView, position: Long) {
        karaokeView.setProgress(position)
        // 更新音乐播放进度、歌词进度等
    }
    override fun onLineFinished(
        view: KaraokeView,
        line: LyricsLineModel,
        score: Int,
        cumulativeScore: Int,
        index: Int,
        lineCount: Int
    ) {
        // 这里可以更新分数UI
    }
})
```

### 3. 解析歌词和音高文件

```kotlin
val lyricFile = File(lyricPath)
val pitchFile = File(pitchPath)
val lyricModel = KaraokeView.parseLyricData(lyricFile, pitchFile, true, lyricOffset)
```

### 4. 设置歌词数据

```kotlin
karaokeView.setLyricData(lyricModel, false)
```

### 5. 同步播放进度

同步播放进度的推荐做法是：

- 使用定时任务（如 ScheduledExecutorService）每隔一定时间（如20ms）获取播放器的当前播放进度。
- 若播放器支持精确进度获取，直接取值；否则可根据上次进度递增。
- 若有歌词或歌曲起始偏移（如mSongOffsetBegin），需加上偏移。
- 通过回调或主线程方法将进度同步到KaraokeView。

参考示例（伪代码，结合MusicManager.kt的startDisplayLrc）：

```kotlin
private fun startDisplayLrc() {
    maybeCreateNewScheduledService()
    mPlayPosition = -1L
    mScheduledExecutorService?.scheduleAtFixedRate({
        if (mStatus == ExampleConstants.Status.Started) {
            if (-1L == mPlayPosition || mPlayPosition % 1000 < MUSIC_POSITION_UPDATE_INTERVAL) {
                mPlayPosition = musicPlayer.getPlayPosition() // 获取播放器进度
                mPlayPosition += mSongOffsetBegin.toLong()   // 加上偏移
            } else {
                mPlayPosition += MUSIC_POSITION_UPDATE_INTERVAL.toLong()
            }
            // 回调到UI线程
            runOnUiThread {
                karaokeView.setProgress(mPlayPosition)
            }
        }
    }, 0, MUSIC_POSITION_UPDATE_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
}
```

- `MUSIC_POSITION_UPDATE_INTERVAL` 可设为20ms，视需求和性能而定。
- 推荐所有UI更新（如setProgress）都在主线程执行。
- 若有歌词/歌曲起始偏移，务必加上。

### 6. 实时音高绘制

在音高回调中，调用 setPitch 传递三个参数：用户音高、分数和进度。参考 MainActivity：

```kotlin
karaokeView.setPitch(
    voicePitch.toFloat(),    // 用户当前音高
    pitchScore.toFloat(),   // 当前音高分数
    progressInMs.toInt()    // 当前播放进度（毫秒）
)
```

- `voicePitch`：用户当前的音高（float）。
- `pitchScore`：当前音高分数（float）。
- `progressInMs`：当前播放进度（int，单位毫秒）。

这些参数通常来源于 MCC 的打分回调，例如：

```kotlin
// IScoreEventHandler 或 IMccCallback 回调
override fun onMusicPitchScore(
    internalSongCode: Long,   // 歌曲内部ID
    voicePitch: Double,       // 用户当前音高
    pitchScore: Double,       // 当前音高分数
    progressInMs: Long        // 当前播放进度（毫秒）
) {
    if (MusicManager.isPlaying()) {
        runOnUiThread {
            karaokeView.setPitch(
                voicePitch.toFloat(),
                pitchScore.toFloat(),
                progressInMs.toInt()
            )
        }
    }
}
```

- `internalSongCode`：MCC内部歌曲唯一ID（一般用于区分多首歌并发场景）。
- `voicePitch`：用户当前音高（Double，需转为Float）。
- `pitchScore`：当前音高分数（Double，需转为Float）。
- `progressInMs`：当前播放进度（Long，需转为Int）。

setPitch 只需传入后三个参数即可。

### 7. 句分与分数显示

可监听 `KaraokeView.setKaraokeEvent`，在 `onLineFinished` 回调中获取每句分数，更新UI。

---

## 四、完整流程示意

1. 初始化 RtcEngine
2. 创建并初始化 IAgoraMusicContentCenter
3. addVendor（音集协/音速达）
4. 创建 IAgoraMusicPlayer
5. 预加载歌曲（mcc.preload）
6. 回调中获取歌词、音高文件路径
7. KaraokeView 解析并显示歌词
8. 打开并播放歌曲（musicPlayer.open/play）
9. 启动打分（mcc.startScore）
10. 实时回调同步进度和音高到 KaraokeView
11. 资源释放

---

## 五、注意事项

- **Token获取**：音速达的token和userId需从你们自己的业务服务器获取。
- **多曲库支持**：addVendor时参数要区分，预加载和打分时要用正确的vendorId和songCode。
- **资源释放**：Activity销毁时要彻底释放所有SDK资源。

---

如需代码片段或某一环节的详细讲解，请随时查阅本文件或联系开发支持。
