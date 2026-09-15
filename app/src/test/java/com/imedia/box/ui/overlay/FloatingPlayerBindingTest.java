package com.imedia.box.ui.overlay;

import androidx.media3.common.Player;

import org.junit.Test;
import org.mockito.InOrder;

import java.util.function.Consumer;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class FloatingPlayerBindingTest {

    @Test
    public void rebuiltPlayerReplacesOldVideoTargetAndListener() {
        Player oldPlayer = mock(Player.class);
        Player newPlayer = mock(Player.class);
        Player.Listener listener = mock(Player.Listener.class);
        @SuppressWarnings("unchecked") Consumer<Player> target = mock(Consumer.class);

        Player result = FloatingPlayerBinding.rebind(oldPlayer, newPlayer, listener, target);

        assertSame(newPlayer, result);
        InOrder order = inOrder(oldPlayer, target, newPlayer);
        order.verify(oldPlayer).removeListener(listener);
        order.verify(target).accept(newPlayer);
        order.verify(newPlayer).addListener(listener);
    }

    @Test
    public void samePlayerDoesNotRebindVideoSurface() {
        Player player = mock(Player.class);
        Player.Listener listener = mock(Player.Listener.class);
        @SuppressWarnings("unchecked") Consumer<Player> target = mock(Consumer.class);

        assertSame(player, FloatingPlayerBinding.rebind(player, player, listener, target));
        verifyNoInteractions(player, listener, target);
    }
}
