package com.imedia.box.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.leanback.widget.VerticalGridView;

import com.imedia.box.R;
import com.imedia.box.setting.Setting;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class HomeSmokeTest {

    @Test
    public void launchesTvHomeWithBrowsableContent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        onView(withId(R.id.title)).check(matches(isDisplayed()));
        onView(withId(R.id.recycler)).check(matches(isAssignableFrom(VerticalGridView.class)));
        assertEquals("iBoxTV", ApplicationProvider.getApplicationContext().getString(R.string.app_name));
    }

    @Test
    public void tvAppNameIsIBoxTvInChineseLocales() {
        Context context = ApplicationProvider.getApplicationContext();
        for (Locale locale : new Locale[]{Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE}) {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(locale);
            assertEquals(locale.toLanguageTag(), "iBoxTV", context.createConfigurationContext(configuration).getString(R.string.app_name));
        }
    }

    @Test
    public void selectedWallpaperIsNotCoveredByPosterBackdrop() {
        int previousWall = Setting.getWall();
        int previousType = Setting.getWallType();
        HomeActivity activity = null;
        try {
            Setting.putWall(2);
            Setting.putWallType(0);
            Intent intent = new Intent(ApplicationProvider.getApplicationContext(), HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity = (HomeActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
            assertEquals(View.GONE, activity.findViewById(R.id.backdrop).getVisibility());
        } finally {
            Setting.putWall(previousWall);
            Setting.putWallType(previousType);
            if (activity != null) activity.finish();
        }
    }
}
