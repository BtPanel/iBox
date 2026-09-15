package com.imedia.box.ui.overlay;

import androidx.media3.common.Player;

import java.util.function.Consumer;

final class FloatingPlayerBinding {

    private FloatingPlayerBinding() {
    }

    static Player rebind(Player current, Player next, Player.Listener listener,
                         Consumer<Player> videoTarget) {
        if (current == next) return current;
        if (current != null) current.removeListener(listener);
        videoTarget.accept(next);
        if (next != null) next.addListener(listener);
        return next;
    }
}
