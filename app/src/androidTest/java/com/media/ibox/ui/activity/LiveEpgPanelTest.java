package com.media.ibox.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.media.ibox.R;
import com.media.ibox.bean.Channel;
import com.media.ibox.bean.Epg;

import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LiveEpgPanelTest {

    private ActivityScenario<LiveActivity> scenario;

    @After
    public void closeScenario() {
        if (scenario != null && !"false".equals(InstrumentationRegistry.getArguments()
                .getString("waitForActivitiesToComplete"))) scenario.close();
    }

    @Test
    public void epgProgramsHaveALayoutManager() {
        scenario = ActivityScenario.launch(LiveActivity.class);
        scenario.onActivity(activity -> {
            RecyclerView programs = activity.findViewById(R.id.epgData);
            assertNotNull("EPG programs cannot render without a LayoutManager", programs.getLayoutManager());
        });
    }

    @Test
    public void backFromEpgRestoresChannelBrowser() {
        scenario = ActivityScenario.launch(LiveActivity.class);
        scenario.onActivity(activity -> {
            View groups = activity.findViewById(R.id.group);
            View channels = activity.findViewById(R.id.channel);
            int panelId = activity.getResources().getIdentifier(
                    "epgPanel", "id", activity.getPackageName());
            int backId = activity.getResources().getIdentifier(
                    "epgBack", "id", activity.getPackageName());

            assertNotEquals("EPG needs a visible panel container", 0, panelId);
            assertNotEquals("EPG needs a dedicated back control", 0, backId);
            View panel = activity.findViewById(panelId);
            View back = activity.findViewById(backId);
            groups.setVisibility(View.GONE);
            channels.setVisibility(View.GONE);
            panel.setVisibility(View.VISIBLE);

            back.performClick();

            assertEquals(View.VISIBLE, groups.getVisibility());
            assertEquals(View.VISIBLE, channels.getVisibility());
            assertEquals(View.GONE, panel.getVisibility());
        });
    }

    @Test
    public void selectedEpgTimeUsesOnPrimaryColor() {
        scenario = ActivityScenario.launch(LiveActivity.class);
        scenario.onActivity(activity -> {
            View row = LayoutInflater.from(activity).inflate(R.layout.adapter_epg_data, null);
            TextView time = row.findViewById(R.id.time);
            time.setSelected(true);

            assertEquals(resolveColor(activity, com.google.android.material.R.attr.colorOnPrimary),
                    time.getCurrentTextColor());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void guideDatesCoverThreeDaysBeforeAndAfterToday() throws Exception {
        Method method = LiveActivity.class.getDeclaredMethod(
                "createGuideDates", Channel.class, ZoneId.class);
        method.setAccessible(true);
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zoneId);

        List<Epg> dates = (List<Epg>) method.invoke(null, new Channel("Test"), zoneId);

        assertEquals(7, dates.size());
        assertEquals(today.minusDays(3).toString(), dates.get(0).getDate());
        assertEquals(today.toString(), dates.get(3).getDate());
        assertEquals(today.plusDays(3).toString(), dates.get(6).getDate());
    }

    private int resolveColor(Context context, int attribute) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attribute, value, true);
        return value.resourceId == 0 ? value.data : ContextCompat.getColor(context, value.resourceId);
    }
}
