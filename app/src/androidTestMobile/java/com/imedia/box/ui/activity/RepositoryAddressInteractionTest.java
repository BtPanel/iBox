package com.imedia.box.ui.activity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.imedia.box.R;
import com.imedia.box.bean.Config;
import com.imedia.box.ui.adapter.RepositoryAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class RepositoryAddressInteractionTest {

    private final AtomicInteger selectionCount = new AtomicInteger();
    private final AtomicInteger otherActionCount = new AtomicInteger();
    private final AtomicReference<Config> selectedItem = new AtomicReference<>();
    private ActivityScenario<RepositoryGestureTestActivity> scenario;
    private Config fixture;

    @Before
    public void showRepositoryRow() {
        // Do not use Config.create(type, url): that overload persists its result.
        fixture = new Config();
        fixture.setId(42);
        fixture.setName("Local gesture fixture");
        fixture.setUrl("https://example.invalid/" + String.join("", Collections.nCopies(30, "long-address-segment/")));
        scenario = ActivityScenario.launch(RepositoryGestureTestActivity.class);
        scenario.onActivity(activity -> {
            RepositoryAdapter adapter = new RepositoryAdapter(new RepositoryAdapter.Listener() {
                @Override
                public void onSelect(Config item) {
                    selectedItem.set(item);
                    selectionCount.incrementAndGet();
                }

                @Override
                public void onEdit(Config item) {
                    otherActionCount.incrementAndGet();
                }

                @Override
                public void onDelete(Config item) {
                    otherActionCount.incrementAndGet();
                }
            });
            adapter.setItems(Collections.singletonList(fixture), null);
            RecyclerView recycler = new RecyclerView(activity);
            recycler.setLayoutManager(new LinearLayoutManager(activity));
            recycler.setAdapter(adapter);
            // Center the fixture away from system bars; keep a real attached view hierarchy.
            FrameLayout window = new FrameLayout(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER);
            window.addView(recycler, params);
            activity.setContentView(window);
        });
        onView(withId(R.id.url)).check(matches(isDisplayed()));
        onView(allOf(isAssignableFrom(HorizontalScrollView.class), hasDescendant(withId(R.id.url))))
                .check((view, error) -> {
                    if (error != null) throw error;
                    HorizontalScrollView viewport = (HorizontalScrollView) view;
                    assertTrue("Fixture must overflow its actual viewport",
                            viewport.getChildAt(0).getWidth() > viewport.getWidth());
                    assertEquals(0, viewport.getScrollX());
                });
    }

    @After
    public void removeFixture() {
        if (scenario == null) return;
        scenario.onActivity(activity -> activity.setContentView(new FrameLayout(activity)));
        // Some desktop emulators kill the instrumented process when its last task closes.
        // Honor the runner's standard opt-out; the invoking script stops the app after results.
        if (!"false".equals(InstrumentationRegistry.getArguments().getString("waitForActivitiesToComplete"))) {
            scenario.close();
        }
    }

    @Test
    public void urlDragScrollsWithoutSelectionAndTapSelectsBoundRepository() {
        onView(allOf(isAssignableFrom(HorizontalScrollView.class), hasDescendant(withId(R.id.url))))
                .perform(swipeLeft())
                .check((view, error) -> {
                    if (error != null) throw error;
                    assertTrue("Horizontal drag must pan the address", view.getScrollX() > 0);
                });
        assertEquals("A drag must not become a repository click", 0, selectionCount.get());
        assertEquals(0, otherActionCount.get());

        scenario.onActivity(activity -> ((HorizontalScrollView) activity.findViewById(R.id.url).getParent()).scrollTo(0, 0));
        // The long TextView extends offscreen. Inject at its viewport's visible center so
        // Android hit testing dispatches the tap to url rather than an offscreen text center.
        onView(allOf(isAssignableFrom(HorizontalScrollView.class), hasDescendant(withId(R.id.url))))
                .perform(click());
        assertEquals("A URL tap must select exactly once", 1, selectionCount.get());
        assertSame(fixture, selectedItem.get());
        assertEquals(0, otherActionCount.get());
    }
}
