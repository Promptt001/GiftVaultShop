package dev.giftshop;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.Map;

final class Alerts {
    enum Cue {
        OFF, PING, HEARTS, SPARKLE, CELEBRATE, DRAIN;

        static Cue parse(String id) {
            return switch (id) {
                case "off" -> OFF;
                case "ping" -> PING;
                case "hearts" -> HEARTS;
                case "sparkle" -> SPARKLE;
                case "celebrate" -> CELEBRATE;
                case "drain" -> DRAIN;
                default -> throw new IllegalArgumentException("Unknown alert cue: " + id);
            };
        }

        void play(ServerPlayer p, Options options) {
            if (this == OFF) return;
            if (options.sounds()) {
                Holder<SoundEvent> sound = switch (this) {
                    case CELEBRATE -> Holder.direct(SoundEvents.PLAYER_LEVELUP);
                    case SPARKLE -> Holder.direct(SoundEvents.AMETHYST_BLOCK_CHIME);
                    case HEARTS -> SoundEvents.NOTE_BLOCK_PLING;
                    default -> Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP);
                };
                playSound(p, sound, 0.4f, this == DRAIN ? 0.55f : 1.15f);
            }
            SimpleParticleType particle = switch (this) {
                case HEARTS -> ParticleTypes.HEART;
                case SPARKLE -> ParticleTypes.END_ROD;
                case CELEBRATE -> ParticleTypes.HAPPY_VILLAGER;
                case DRAIN -> ParticleTypes.SMOKE;
                default -> null;
            };
            if (options.particles() && particle != null) {
                var eye = p.getEyePosition().add(p.getLookAngle().scale(2));
                p.level().sendParticles(p, particle, false, false,
                    eye.x, eye.y, eye.z, 10, 0.35, 0.22, 0.35, 0.02);
            }
        }
    }

    static void purchase(ServerPlayer player, boolean sounds) {
        if (sounds) playSound(player, Holder.direct(SoundEvents.VILLAGER_YES), 0.45f, 1.0f);
    }

    private static void playSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS,
            player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    record Options(boolean sounds, boolean particles, int cooldownTicks, Map<String, Cue> events) {
        static Options parse(JsonObject input) {
            var defaults = Map.of("comment", Cue.OFF, "join", Cue.OFF, "like", Cue.OFF,
                "follow", Cue.HEARTS, "share", Cue.CELEBRATE, "gift", Cue.SPARKLE);
            if (input == null) return new Options(true, true, 20, defaults);
            var events = new java.util.HashMap<>(defaults);
            var configured = input.getAsJsonObject("events");
            if (configured != null) for (var entry : configured.entrySet()) {
                if (!defaults.containsKey(entry.getKey())) throw new IllegalArgumentException("Unknown alert event: " + entry.getKey());
                events.put(entry.getKey(), Cue.parse(entry.getValue().getAsString()));
            }
            return new Options(Settings.flag(input, "sounds", true), Settings.flag(input, "particles", true),
                Settings.integer(input, "cooldownTicks", 5, 200, 20), Map.copyOf(events));
        }
    }

    // Only the optional cue is throttled. The integrated server thread owns this state.
    static final class Cooldown {
        private Integer lastTick;
        boolean allow(int tick, int interval) {
            if (lastTick != null && tick - lastTick < interval) return false;
            lastTick = tick;
            return true;
        }
        void reset() { lastTick = null; }
    }
}
