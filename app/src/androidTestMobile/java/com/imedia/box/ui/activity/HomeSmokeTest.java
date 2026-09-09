package com.imedia.box.ui.activity;

import android.content.Intent;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.imedia.box.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HomeSmokeTest {

    @Test
    public void launchesMobileHomeWithNavigation() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        onView(withId(R.id.container)).check(matches(isDisplayed()));
        onView(withId(R.id.navigation)).check(matches(isDisplayed()));
        assertEquals("iBox", ApplicationProvider.getApplicationContext().getString(R.string.app_name));
    }
}
