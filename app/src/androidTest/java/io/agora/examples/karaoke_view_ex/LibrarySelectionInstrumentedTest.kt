package io.agora.examples.karaoke_view_ex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.agora.examples.karaoke_view_ex.agora.RtcManager
import io.agora.examples.karaoke_view_ex.agora.ServiceManager
import io.agora.examples.utils.ServiceType
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySelectionInstrumentedTest {
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

    private fun launcherIntent(): Intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))

    private fun assertChoice(activity: Activity) {
        assertEquals("${context.packageName}.LibrarySelectionActivity", activity.javaClass.name)
        val engine = RtcManager::class.java.getDeclaredField("mRtcEngine").apply { isAccessible = true }
        assertNull("The selection screen must not initialize RTC", engine.get(null))
    }

    @Test fun launcherAlwaysShowsChoiceBeforeSdkInitialization() {
        prefs.edit().putInt(serviceKey, ServiceType.TME.type).commit()
        ActivityScenario.launch<Activity>(launcherIntent()).use { scenario ->
            scenario.onActivity { assertChoice(it) }
            onView(withText("TME")).check(matches(isDisplayed()))
            onView(withText("音集协")).check(matches(isDisplayed()))
            scenario.recreate()
            scenario.onActivity { assertChoice(it) }
        }
    }

    @Test fun tmeChoiceOverridesPreviousMccAndReturnsToSelector() {
        prefs.edit().putInt(serviceKey, ServiceType.MCC.type).commit()
        ActivityScenario.launch<Activity>(launcherIntent()).use { scenario ->
            scenario.onActivity { assertChoice(it) }
            onView(withText("TME")).perform(click())
            onView(withId(R.id.tme_list_container)).check(matches(isDisplayed()))
            onView(withId(R.id.play)).check(matches(not(isDisplayed())))
            assertEquals(ServiceType.TME, ServiceManager.getServiceType())
            pressBack()
            onView(withText("音集协")).check(matches(isDisplayed()))
            scenario.onActivity { assertChoice(it) }
            onView(withText("音集协")).perform(click())
            onView(withId(R.id.play)).check(matches(isDisplayed()))
            assertEquals(ServiceType.MCC, ServiceManager.getServiceType())
            pressBack()
            scenario.onActivity { assertChoice(it) }
        }
    }

    @Test fun mccChoiceOverridesPreviousTmeAndKeepsExistingControls() {
        prefs.edit().putInt(serviceKey, ServiceType.TME.type).commit()
        ActivityScenario.launch<Activity>(launcherIntent()).use { scenario ->
            scenario.onActivity { assertChoice(it) }
            onView(withText("音集协")).perform(click())
            onView(withId(R.id.play)).check(matches(isDisplayed()))
            onView(withId(R.id.pause)).check(matches(isDisplayed()))
            onView(withId(R.id.switch_to_next)).check(matches(isDisplayed()))
            onView(withId(R.id.tme_list_container)).check(matches(not(isDisplayed())))
            assertEquals(ServiceType.MCC, ServiceManager.getServiceType())
            pressBack()
            onView(withText("TME")).check(matches(isDisplayed()))
            scenario.onActivity { assertChoice(it) }
        }
    }

    @Test fun rapidChoicesOpenOnlyOneLibraryScreen() {
        ActivityScenario.launch<Activity>(launcherIntent()).use { scenario ->
            scenario.onActivity { activity ->
                assertChoice(activity)
                activity.findViewById<View>(R.id.choose_tme).performClick()
                activity.findViewById<View>(R.id.choose_mcc).performClick()
            }
            onView(withId(R.id.tme_list_container)).check(matches(isDisplayed()))
            assertEquals(ServiceType.TME, ServiceManager.getServiceType())
            pressBack()
            onView(withText("音集协")).check(matches(isDisplayed()))
            scenario.onActivity { assertChoice(it) }
        }
    }

    @Test fun mainRecreationKeepsLaterSettingsChoiceInsteadOfReapplyingLaunchChoice() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SERVICE_TYPE, ServiceType.TME.type)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            onView(withId(R.id.tme_list_container)).check(matches(isDisplayed()))
            prefs.edit().putInt(serviceKey, ServiceType.MCC.type).commit()
            scenario.recreate()
            onView(withId(R.id.play)).check(matches(isDisplayed()))
            onView(withId(R.id.tme_list_container)).check(matches(not(isDisplayed())))
            assertEquals(ServiceType.MCC, ServiceManager.getServiceType())
        }
    }
}
