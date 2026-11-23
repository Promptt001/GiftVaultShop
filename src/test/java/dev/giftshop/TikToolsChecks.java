package dev.giftshop;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

final class TikToolsChecks {
    private static final String RECIPIENT = "6776000000000000010";
    private static final String TX = "7789000000000000031";
    private static final String ROOM = "{\"event\":\"roomInfo\",\"roomId\":\"7789000000000000010\"}";
    private static final String GIFT = """
        {"event":"gift","data":{"giftId":5655,"giftName":"Rose","user":{"uniqueId":"tester"},
        "receiverUserId":"%s","transactionId":%s,"repeatEnd":true,"repeatCount":3}}
        """.formatted(RECIPIENT, TX);

    static void run() throws Exception {
        var rewardMode = config("rewards");
        var readOnly = config("read_only");
        var gift = TikToolsEvent.parse(GIFT).gift();
        require(gift.transaction().equals(TX), "64-bit transaction ID must not go through floating point");
        var reward = gift.reward(rewardMode);
        require(gift.reward(readOnly) == null, "read-only cannot request rewards");
        require(reward.rule().equals("rose") && reward.count() == 3, "reward requires mapped ID and uses final count");
        require(TikToolsEvent.parse(GIFT.replace(TX, '"' + TX + '"')).gift().reward(rewardMode).key().equals(reward.key()), "numeric and text ID match");
        for (String bad : List.of(GIFT.replace("true", "false"), GIFT.replace("true", "\"true\""),
            GIFT.replace("\"transactionId\":" + TX + ",", ""), GIFT.replace(RECIPIENT, "999"),
            GIFT.replace(RECIPIENT, ""), GIFT.replace("5655", "5656"), GIFT.replace(":3}", ":1000}"), GIFT.replace(":3}", ":3.5}"))) {
            require(TikToolsEvent.parse(bad).gift().reward(rewardMode) == null, "unsafe gift must not request a reward");
        }
        require(TikToolsEvent.parse("nope") == null && TikToolsEvent.parse("{\"event\":\"roomInfo\"}") == null, "malformed input");
        for (var pair : Map.of("chat", "comment", "follow", "follow", "share", "share", "member", "join", "like", "like").entrySet()) {
            var event = TikToolsEvent.parse("{\"event\":\"" + pair.getKey() + "\",\"data\":{\"comment\":\"/op someone\",\"likeCount\":4}}");
            require(event.kind().equals(pair.getValue()) && event.gift() == null, "activity routing is display-only");
        }
        require(rewardMode.endpoint("a&b").toString().equals("wss://api.tik.tools?uniqueId=tester&apiKey=a%26b"), "fixed endpoint and URL encoding");
        require(TikToolsConfig.parse(JsonParser.parseString("{\"mode\":\"off\"}").getAsJsonObject(), Set.of()).mode() == TikToolsConfig.Mode.OFF, "off needs no key or username");
        loopback(rewardMode);
        System.out.println("PASS: TikTools event routing, reward refusals, reconnect, duplicate and wallet reload over loopback WebSocket");
    }

    private static TikToolsConfig config(String mode) {
        return TikToolsConfig.parse(JsonParser.parseString("""
            {"mode":"%s","username":"@tester","recipientUserId":"%s","giftRules":{"5655":"rose"}}
            """.formatted(mode, RECIPIENT)).getAsJsonObject(), Set.of("rose"));
    }

    private static void loopback(TikToolsConfig config) throws Exception {
        var clock = new AtomicLong();
        try (var listener = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
             var feed = new TikToolsFeed(URI.create("ws://127.0.0.1:" + listener.getLocalPort()), clock::get);
             var executor = Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(4_000);
            var provider = executor.submit(() -> {
                try (var first = accept(listener)) {
                    send(first, 0x81, GIFT); // ignored before room confirmation
                    send(first, 0x81, ROOM);
                    send(first, 0x81, GIFT.replace("true", "false"));
                    send(first, 0x01, GIFT.substring(0, 60)); // fragmented delivery
                    send(first, 0x80, GIFT.substring(60));
                    send(first, 0x88, new byte[]{3, (byte) 232});
                }
                try (var second = accept(listener)) {
                    send(second, 0x81, ROOM);
                    send(second, 0x81, GIFT);
                    for (int i = 0; i < 260; i++) send(second, 0x81, "{\"event\":\"like\",\"data\":{\"likeCount\":1}}");
                    for (int i = 0; i < 129; i++) send(second, 0x81, GIFT);
                    second.setSoTimeout(4_000);
                    require(second.getInputStream().read() == -1, "closing the feed closes the wire");
                }
                return null;
            });
            feed.tick();
            await(() -> feed.status().contains("may have been missed"));
            var first = feed.drain();
            require(first.size() == 1, "only the finalized gift after room readiness is delivered");
            var a = first.getFirst().gift().reward(config);
            var w = Wallet.EMPTY.gift(a.key(), 5, a.count());
            require(w.points() == 15, "one finalized streak credits the configured per-unit amount");
            var saved = Wallet.CODEC.encodeStart(JsonOps.INSTANCE, w).getOrThrow();
            var reopened = Wallet.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
            clock.addAndGet(1_000);
            feed.tick();
            await(feed::rewardsPaused);
            var b = feed.drain().getFirst().gift().reward(config);
            require(reopened.gift(b.key(), -5, b.count()) == reopened, "replayed gift cannot change the saved balance");
            require(feed.status().contains("activity messages dropped: 4") && feed.status().contains("overflow"), "bounded queues and visible loss");
            feed.close();
            require(feed.drain().isEmpty(), "stop discards queued events");
            provider.get(5, TimeUnit.SECONDS);
        }
    }

    private static Socket accept(ServerSocket server) throws Exception {
        var socket = server.accept();
        socket.setSoTimeout(4_000);
        try {
            var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String line, key = null;
            while ((line = input.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) key = line.substring(line.indexOf(':') + 1).trim();
            }
            require(key != null, "client sent a WebSocket handshake");
            var hash = MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
            var response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                + Base64.getEncoder().encodeToString(hash) + "\r\n\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            return socket;
        } catch (Exception | AssertionError e) { socket.close(); throw e; }
    }
    private static void send(Socket socket, int opcode, String text) throws IOException { send(socket, opcode, text.getBytes(StandardCharsets.UTF_8)); }
    private static void send(Socket socket, int opcode, byte[] bytes) throws IOException {
        var data = new DataOutputStream(socket.getOutputStream());
        data.writeByte(opcode);
        if (bytes.length < 126) data.writeByte(bytes.length);
        else { data.writeByte(126); data.writeShort(bytes.length); }
        data.write(bytes);
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        var end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        require(condition.getAsBoolean(), "timed out waiting for WebSocket state");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
