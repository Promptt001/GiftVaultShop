package dev.giftshop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

final class TikToolsFeed implements AutoCloseable {
    private static final int MAX_FRAME_CHARS = 65_536;
    private static final int MAX_GIFTS = 128, MAX_ACTIVITY = 256;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final URI endpoint;
    private final LongSupplier clock;
    private final ArrayDeque<TikToolsEvent> gifts = new ArrayDeque<>(), activity = new ArrayDeque<>();
    private Attempt attempt;
    private boolean stopped, rejected, gap, giftOverflow;
    private long nextTry, retries, droppedActivity;
    private String state = "waiting to connect";

    TikToolsFeed(URI endpoint) { this(endpoint, () -> System.nanoTime() / 1_000_000); }
    TikToolsFeed(URI endpoint, LongSupplier clock) { this.endpoint = endpoint; this.clock = clock; nextTry = clock.getAsLong(); }

    synchronized void tick() {
        if (stopped || rejected) return;
        long now = clock.getAsLong();
        if (attempt == null) {
            if (now < nextTry) return;
            var a = new Attempt(now);
            attempt = a;
            state = "connecting";
            a.pending = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(endpoint, a);
            a.pending.whenComplete((ws, error) -> {
                if (error == null) return;
                Throwable cause = error.getCause() == null ? error : error.getCause();
                int status = cause instanceof WebSocketHandshakeException h ? h.getResponse().statusCode() : 0;
                failed(a, status == 401 || status == 403 ? "credentials or access rejected; reopen after fixing" : "connection failed",
                    status == 429 ? 60_000 : 0, status == 401 || status == 403);
            });
        } else if (now - attempt.lastSeen >= (attempt.roomReady ? 45_000 : 20_000)) {
            failed(attempt, "connection timed out", 0, false);
        } else if (attempt.socket != null && now - attempt.lastPing >= 15_000) {
            var a = attempt;
            a.lastPing = now;
            a.socket.sendPing(ByteBuffer.allocate(0)).exceptionally(error -> { failed(a, "heartbeat failed", 0, false); return null; });
        }
    }

    private synchronized void failed(Attempt a, String reason, long minimum, boolean terminal) {
        if (attempt != a || stopped) return;
        attempt = null;
        if (a.roomReady) gap = true;
        a.cancel();
        rejected = terminal;
        long delay = Math.max(minimum, Math.min(60_000, 1_000L << (int) Math.min(retries++, 6)));
        nextTry = clock.getAsLong() + delay;
        state = reason + (terminal ? "" : "; retrying in " + delay / 1_000 + "s");
    }

    synchronized String status() {
        return state + (gap ? " · events may have been missed" : "")
            + (giftOverflow ? " · gift queue overflow: rewards paused until world reopen" : "")
            + (droppedActivity > 0 ? " · activity messages dropped: " + droppedActivity : "");
    }
    synchronized boolean rewardsPaused() { return giftOverflow; }
    synchronized List<TikToolsEvent> drain() {
        var events = new ArrayList<TikToolsEvent>();
        for (int i = 0; i < 16 && !gifts.isEmpty(); i++) events.add(gifts.removeFirst());
        for (int i = 0; i < 4 && !activity.isEmpty(); i++) events.add(activity.removeFirst());
        return events;
    }
    @Override public synchronized void close() {
        stopped = true;
        var a = attempt;
        attempt = null;
        if (a != null) a.cancel();
        client.shutdownNow();
        gifts.clear();
        activity.clear();
        state = "stopped";
    }

    private final class Attempt implements WebSocket.Listener {
        final StringBuilder frame = new StringBuilder();
        CompletableFuture<WebSocket> pending;
        WebSocket socket;
        long lastSeen, lastPing;
        boolean roomReady;
        Attempt(long now) { lastSeen = lastPing = now; }
        void cancel() {
            if (socket != null) socket.abort();
            if (pending != null) pending.cancel(true);
        }
        @Override public void onOpen(WebSocket ws) {
            synchronized (TikToolsFeed.this) {
                if (attempt != this || stopped) { ws.abort(); return; }
                socket = ws;
                state = "socket connected; waiting for LIVE room";
                ws.request(1);
            }
        }
        @Override public CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
            synchronized (TikToolsFeed.this) {
                if (attempt != this || stopped) return null;
                if (data.length() > MAX_FRAME_CHARS - frame.length()) {
                    failed(this, "message exceeded size limit", 0, false);
                    return null;
                }
                frame.append(data);
                if (last) {
                    var event = TikToolsEvent.parse(frame.toString());
                    frame.setLength(0);
                    if (event != null && event.kind().equals("roomInfo")) {
                        roomReady = true;
                        retries = 0;
                        state = "LIVE room connected";
                    } else if (event != null && roomReady) {
                        if (event.gift() == null) {
                            if (activity.size() == MAX_ACTIVITY) { activity.removeFirst(); droppedActivity++; }
                            activity.addLast(event);
                        } else if (event.gift().finalized()) {
                            if (gifts.size() == MAX_GIFTS) { giftOverflow = true; gap = true; }
                            else gifts.addLast(event);
                        }
                    }
                }
                // Before roomInfo, heartbeats must not extend the room-ready deadline.
                if (roomReady) lastSeen = clock.getAsLong();
                ws.request(1);
            }
            return null;
        }
        @Override public CompletableFuture<?> onPong(WebSocket ws, ByteBuffer message) {
            synchronized (TikToolsFeed.this) {
                if (attempt == this && !stopped) {
                    if (roomReady) lastSeen = clock.getAsLong();
                    ws.request(1);
                }
            }
            return null;
        }
        @Override public CompletableFuture<?> onClose(WebSocket ws, int code, String ignored) {
            failed(this, "connection closed (" + code + ")", code == 4404 || code == 4005 ? 60_000 : 0, code == 4401 || code == 4403);
            return null;
        }
        @Override public void onError(WebSocket ws, Throwable ignored) { failed(this, "connection interrupted", 0, false); }
    }
}
