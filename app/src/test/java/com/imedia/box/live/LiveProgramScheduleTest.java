package com.imedia.box.live;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Epg;

import org.junit.Test;

import java.util.List;

public class LiveProgramScheduleTest {

    @Test
    public void switchesCurrentProgramAtItsEndBoundary() {
        EpgData first = program(1_000, 2_000);
        EpgData second = program(2_000, 3_000);

        LiveProgramSchedule.Snapshot before = LiveProgramSchedule.select(List.of(first, second), 1_999);
        assertSame(first, before.current());
        assertSame(second, before.next());

        LiveProgramSchedule.Snapshot atBoundary = LiveProgramSchedule.select(List.of(first, second), 2_000);
        assertSame(second, atBoundary.current());
        assertNull(atBoundary.next());
    }

    @Test
    public void showsUpcomingProgramWhenNothingIsOnAirAndIgnoresUnknownTimes() {
        EpgData unknown = program(0, 0);
        EpgData later = program(4_000, 5_000);
        EpgData sooner = program(2_000, 3_000);

        LiveProgramSchedule.Snapshot snapshot = LiveProgramSchedule.select(List.of(unknown, later, sooner), 1_000);
        assertNull(snapshot.current());
        assertSame(sooner, snapshot.next());
        LiveProgramSchedule.Snapshot empty = LiveProgramSchedule.select(List.of(), 1_000);
        assertNull(empty.current());
        assertNull(empty.next());
    }

    @Test
    public void channelOverviewIncludesProgramsAcrossCachedDates() {
        EpgData overnight = program(1_000, 3_000);
        EpgData morning = program(3_000, 4_000);
        Epg yesterday = Epg.create("news", "2026-09-12");
        yesterday.setList(List.of(overnight));
        Epg today = Epg.create("news", "2026-09-13");
        today.setList(List.of(morning));
        Channel channel = Channel.create("News");
        channel.setDataList(List.of(yesterday, today));

        LiveProgramSchedule.Snapshot snapshot = LiveProgramSchedule.select(channel, 2_500);

        assertSame(overnight, snapshot.current());
        assertSame(morning, snapshot.next());
    }

    private EpgData program(long start, long end) {
        EpgData data = new EpgData();
        data.setStartTime(start);
        data.setEndTime(end);
        return data;
    }
}
