package dev.giftshop;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

final class PortraitAlerts {
    record Options(boolean enabled, int verticalPercent, Set<String> events) {
        private static final Set<String> DEFAULTS = Set.of("gift", "follow", "share", "like");
        static Options parse(JsonObject config) {
            if (config == null) return new Options(true, 15, DEFAULTS);
            var flags = config.getAsJsonObject("events");
            var enabledEvents = new HashSet<String>();
            for (String kind : Settings.KINDS)
                if (Settings.flag(flags, kind, DEFAULTS.contains(kind))) enabledEvents.add(kind);
            return new Options(Settings.flag(config, "enabled", true),
                Settings.integer(config, "verticalPercent", 0, 100, 15), Set.copyOf(enabledEvents));
        }

        boolean shows(String kind) { return enabled && events.contains(kind); }
    }

    record Box(int x, int y, int width, int height) {
        static Box layout(int width, int height, int percent) {
            int cropWidth = Math.min(width, (int) (height * 9L / 16));
            // Keep four GUI units from each crop edge, with room for rounding at odd scales.
            int bannerWidth = Math.min(240, cropWidth - 8);
            if (bannerWidth < 40 || height < 48) return null;
            int y = 6 + (int) ((height - 48) * (long) Math.clamp(percent, 0, 100) / 100);
            return new Box((width - bannerWidth) / 2, y, bannerWidth, 36);
        }
    }

    static final class Queue {
        private static final int LIMIT = 6;
        private static final long MAX_AGE = 12_000;
        private final ArrayDeque<Pending> gifts = new ArrayDeque<>(), activity = new ArrayDeque<>();
        private final Likes liveLikes = new Likes(), testLikes = new Likes();
        private AlertBannerPacket active;
        private long until;
        private record Pending(AlertBannerPacket alert, long received) {}

        void offer(AlertBannerPacket alert, long now) {
            if (alert.likes() > 0) {
                (alert.live() ? liveLikes : testLikes).add(alert, now);
                return;
            }
            ArrayDeque<Pending> queue = alert.kind().equals("gift") ? gifts : activity;
            if (queue.size() == LIMIT) queue.removeFirst();
            queue.addLast(new Pending(alert, now));
        }

        AlertBannerPacket current(long now) {
            // Gifts interrupt ordinary activity; they do not cut off a gift already being shown.
            if (active != null && now < until) {
                if (active.kind().equals("gift")) return active;
                var priority = take(gifts, now);
                if (priority == null) return active;
                active = priority;
                until = now + 3_000;
                return active;
            }
            active = take(gifts, now);
            if (active == null) active = take(activity, now);
            if (active == null) active = liveLikes.take(now);
            if (active == null) active = testLikes.take(now);
            if (active != null) until = now + (active.kind().equals("gift") ? 3_000 : 2_000);
            return active;
        }

        private static AlertBannerPacket take(ArrayDeque<Pending> queue, long now) {
            while (!queue.isEmpty()) {
                var pending = queue.removeFirst();
                if (now - pending.received() < MAX_AGE) return pending.alert();
            }
            return null;
        }

        void clear() {
            gifts.clear(); activity.clear(); liveLikes.clear(); testLikes.clear(); active = null; until = 0;
        }

        private static final class Likes {
            private int count, position;
            private long first, last;
            private boolean live;
            void add(AlertBannerPacket alert, long now) {
                if (count == 0 || now - last >= MAX_AGE) { first = now; count = 0; }
                count = Math.min(1_000_000_000, count + alert.likes());
                last = now; live = alert.live(); position = alert.verticalPercent();
            }
            AlertBannerPacket take(long now) {
                if (count == 0) return null;
                if (now - last >= MAX_AGE) { clear(); return null; }
                if (now - first < 800) return null;
                var alert = new AlertBannerPacket("like", count + (count == 1 ? " like" : " likes"), "", live, position, 0);
                clear();
                return alert;
            }
            void clear() { count = 0; }
        }
    }
}
