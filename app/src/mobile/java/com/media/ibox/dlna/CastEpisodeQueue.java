package com.media.ibox.dlna;

import java.util.List;

public final class CastEpisodeQueue {

    private final String siteKey;
    private final String flag;
    private final List<Item> items;
    private final boolean reversed;
    private int index;

    public CastEpisodeQueue(String siteKey, String flag, List<Item> items, int index, boolean reversed) {
        if (items == null || items.isEmpty() || index < 0 || index >= items.size()) throw new IllegalArgumentException("Invalid cast episode queue");
        this.siteKey = siteKey;
        this.flag = flag;
        this.items = List.copyOf(items);
        this.index = index;
        this.reversed = reversed;
    }

    public String siteKey() {
        return siteKey;
    }

    public String flag() {
        return flag;
    }

    public Item current() {
        return items.get(index);
    }

    public Candidate next() {
        return at(index + (reversed ? -1 : 1));
    }

    public Candidate previous() {
        return at(index + (reversed ? 1 : -1));
    }

    public boolean hasNext() {
        return next() != null;
    }

    public boolean hasPrevious() {
        return previous() != null;
    }

    public void select(Candidate candidate) {
        if (candidate == null || at(candidate.index()) == null) throw new IllegalArgumentException("Invalid episode");
        index = candidate.index();
    }

    private Candidate at(int target) {
        return target >= 0 && target < items.size() ? new Candidate(target, items.get(target)) : null;
    }

    public record Item(String name, String url) {
    }

    public record Candidate(int index, Item item) {
    }
}
