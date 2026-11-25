package dev.giftshop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

record Wallet(int points, Set<String> gifts, Map<String, Long> likeTotals) {
    static final int LIMIT = 1_000_000_000;
    static final Pattern EVENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    static final Wallet EMPTY = new Wallet(0, Set.of(), Map.of());
    private static final int MAX_LIKE_ROOMS = 4096;
    private static final Codec<Set<String>> IDS = Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf);
    static final Codec<Wallet> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.intRange(0, LIMIT).fieldOf("points").forGetter(Wallet::points),
        IDS.fieldOf("gifts").forGetter(Wallet::gifts),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("likeTotals", Map.of()).forGetter(Wallet::likeTotals)
    ).apply(i, Wallet::new));

    Wallet {
        if (points < 0 || points > LIMIT) throw new IllegalArgumentException("Invalid points balance");
        gifts = Set.copyOf(gifts);
        likeTotals = Map.copyOf(likeTotals);
    }

    Wallet gift(String id, int rule, int count) {
        if (!EVENT_ID.matcher(id).matches() || count < 1 || count > 999 || Math.abs((long) rule) > LIMIT)
            throw new IllegalArgumentException("Invalid gift event");
        if (gifts.contains(id)) return this;
        var credited = new HashSet<>(gifts);
        credited.add(id);
        long balance = points + (long) rule * count;
        return new Wallet((int) Math.clamp(balance, 0, LIMIT), credited, likeTotals);
    }

    Wallet liveLikes(TikToolsConfig config, String room, long total, int batch, boolean rewardsPaused) {
        if (config.mode() != TikToolsConfig.Mode.REWARDS || rewardsPaused || room.isEmpty()
            || total < 0 || batch <= 0) return this;
        Long previous = likeTotals.get(room);
        if (previous == null ? likeTotals.size() >= MAX_LIKE_ROOMS : total <= previous) return this;
        // The first report is only a baseline. Do not award unobserved batches across a gap.
        long delta = previous == null ? 0 : Math.min(total - previous, batch);
        var next = new HashMap<>(likeTotals);
        next.put(room, total);
        return new Wallet((int) Math.min(LIMIT, points + delta), gifts, next);
    }

    Wallet spend(int price) {
        if (price <= 0 || price > points) throw new IllegalArgumentException("Not enough points or invalid price");
        return new Wallet(points - price, gifts, likeTotals);
    }
}
