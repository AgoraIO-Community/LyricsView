package io.agora.examples.karaoke_view_ex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.examples.utils.ServiceType
import io.agora.karaoke_view_ex.internal.constants.LyricType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoNavigationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: SharedPreferences
    private lateinit var serviceKey: String
    private lateinit var lyricKey: String
    private var previousService: Int? = null
    private var previousLyric: Int? = null

    @Before fun savePreferences() {
        prefs = context.getSharedPreferences("karaoke_sample_app", Context.MODE_PRIVATE)
        serviceKey = context.getString(R.string.prefs_key_service_type)
        lyricKey = context.getString(R.string.prefs_key_lyric_type)
        previousService = if (prefs.contains(serviceKey)) prefs.getInt(serviceKey, 0) else null
        previousLyric = if (prefs.contains(lyricKey)) prefs.getInt(lyricKey, 0) else null
    }

    @After fun restorePreferences() {
        val editor = prefs.edit()
        previousService?.let { editor.putInt(serviceKey, it) } ?: editor.remove(serviceKey)
        previousLyric?.let { editor.putInt(lyricKey, it) } ?: editor.remove(lyricKey)
        editor.commit()
    }

    @Test fun launcherDefaultsToNativeMccSingingWithoutLibraryEntry() {
        prefs.edit().remove(serviceKey).remove(lyricKey).commit()
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        ActivityScenario.launch<Activity>(launch).use { scenario ->
            scenario.onActivity {
                assertEquals(MainActivity::class.java.name, it.javaClass.name)
                assertEquals("音集协演唱", it.title.toString())
                assertEquals(ServiceType.MCC, ServiceManager.getServiceType())
                assertEquals(LyricType.XML.ordinal, prefs.getInt(lyricKey, -1))
                val plugin = ServiceManager::class.java.getDeclaredField("mMccExManager").apply { isAccessible = true }
                assertNull("Native entry must not initialize MCCEx", plugin.get(null))
            }
            onView(withText("选歌")).check(matches(isDisplayed()))
            onView(withText("设置")).check(matches(isDisplayed()))
        }
    }

    @Test fun legacyMccExPreferenceMigratesToNativeMcc() {
        prefs.edit().putInt(serviceKey, ServiceType.MCC_EX.type).putInt(lyricKey, LyricType.KRC.ordinal).commit()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity {
                assertEquals(ServiceType.MCC, ServiceManager.getServiceType())
                assertEquals(ServiceType.MCC.type, prefs.getInt(serviceKey, -1))
                assertEquals(LyricType.XML.ordinal, prefs.getInt(lyricKey, -1))
            }
        }
    }

    @Test fun nativeMccSheetIsEightyPercentHasNoSettingsAndSelectsActualRow() {
        prefs.edit().putInt(serviceKey, ServiceType.MCC_EX.type).commit()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            onView(withText("选歌")).perform(click())
            onView(withText("音集协 选歌")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("设置")).inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            scenario.onActivity { activity ->
                val sheet = activity.supportFragmentManager.fragments.filterIsInstance<DialogFragment>().single()
                val view = requireNotNull(sheet.dialog).findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.getRealMetrics(metrics)
                assertEquals(metrics.heightPixels * .8, view.height.toDouble(), 2.0)
                val list = requireNotNull(sheet.view).findViewById<android.widget.ListView>(R.id.song_list)
                assertEquals(listOf("爱情转移", "说爱你", "love story"),
                    (0 until list.adapter.count).map { list.adapter.getItem(it).toString() })
            }
            pressBack()
            onView(withText("选歌")).check(matches(isDisplayed())).perform(click())
            onView(withText("说爱你")).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertTrue(activity.supportFragmentManager.fragments.filterIsInstance<DialogFragment>().isEmpty())
                val index = ServiceManager::class.java.getDeclaredField("mCurrentSongCodeIndex").apply { isAccessible = true }
                assertEquals(1, index.getInt(null))
            }
        }
    }

    @Test fun settingsHasTwoModesAndSwitchesBetweenSingingScreensIncludingSystemBack() {
        prefs.edit().putInt(serviceKey, ServiceType.MCC_EX.type).commit()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            onView(withText("设置")).perform(click())
            onView(withId(R.id.service_type_radio_group)).check { view, _ ->
                assertEquals(2, (view as RadioGroup).childCount)
            }
            onView(withText("音集协")).check(matches(isDisplayed()))
            onView(withId(R.id.radio_lyric_xml)).check(matches(isChecked())).check(matches(isEnabled()))
            onView(withId(R.id.radio_lyric_krc)).check(matches(isNotEnabled()))
            onView(withText("TME")).perform(click())
            onView(withId(R.id.radio_lyric_lrc)).check(matches(isChecked()))
            pressBack()
            scenario.onActivity { activity ->
                assertEquals("TME 演唱", activity.title.toString())
                assertEquals(ServiceType.TME, ServiceManager.getServiceType())
                activity.onTmeSongChanged("旧歌曲")
                activity.findViewById<android.widget.TextView>(R.id.playing_progress).text = "123"
            }
            onView(withText("选歌")).perform(click())
            onView(withText("TME 选歌")).inRoot(isDialog()).check(matches(isDisplayed()))
            pressBack()
            onView(withText("设置")).perform(click())
            onView(withText("音集协")).perform(click())
            onView(withId(R.id.settings_done)).perform(click())
            scenario.onActivity {
                assertEquals("音集协演唱", it.title.toString())
                assertEquals(ServiceType.MCC, ServiceManager.getServiceType())
                assertEquals(LyricType.XML.ordinal, prefs.getInt(lyricKey, -1))
                assertEquals("", it.findViewById<android.widget.TextView>(R.id.callBack).text.toString())
                assertEquals("", it.findViewById<android.widget.TextView>(R.id.playing_progress).text.toString())
            }
        }
    }

    @Test fun openSongSheetRemainsEightyPercentAfterRotation() {
        prefs.edit().putInt(serviceKey, ServiceType.MCC_EX.type).commit()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            onView(withText("选歌")).perform(click())
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE &&
                android.os.SystemClock.uptimeMillis() < deadline) {
                android.os.SystemClock.sleep(50)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            onView(withText("音集协 选歌")).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
                val sheet = activity.supportFragmentManager.fragments.filterIsInstance<DialogFragment>().single()
                val view = requireNotNull(sheet.dialog).findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.getRealMetrics(metrics)
                assertEquals(metrics.heightPixels * .8, view.height.toDouble(), 2.0)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    @Test fun tmePreferenceAndOpenSheetSurviveRecreationWithoutStartingASong() {
        prefs.edit().putInt(serviceKey, ServiceType.TME.type).putInt(lyricKey, LyricType.LRC.ordinal).commit()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            onView(withText("选歌")).perform(click())
            scenario.recreate()
            onView(withText("TME 选歌")).inRoot(isDialog()).check(matches(isDisplayed()))
            pressBack()
            scenario.onActivity {
                assertEquals("TME 演唱", it.title.toString())
                assertEquals(ServiceType.TME, ServiceManager.getServiceType())
                for (id in listOf(R.id.play, R.id.pause, R.id.switch_to_next, R.id.skip_the_intro,
                    R.id.play_original, R.id.enable_lyrics, R.id.enable_scoring)) {
                    assertEquals(android.view.View.VISIBLE, it.findViewById<android.view.View>(id).visibility)
                }
                assertNull(ServiceManager.getTmeController(""))
            }
        }
    }
}
