package dev.giftshop;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

final class ShopMenu extends AbstractContainerMenu {
    static final int PER_PAGE = 6;
    static final int PREVIOUS = 6, NEXT = 7, BACK = 8, BUY = 9;
    static final int W = 294, H = 206, CARD_W = 88, CARD_H = 66;
    static int cardX(int i) { return 11 + i % 3 * 92; }
    static int cardY(int i) { return 37 + i / 3 * 71; }

    static final MenuType<ShopMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
        Identifier.parse("fabric_gift_shop:shop"), new MenuType<>(ShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private final ServerPlayer owner;
    private final List<Settings.Offer> offers;
    private final boolean sound;
    private final SimpleContainer display = new SimpleContainer(PER_PAGE);
    // Container data is sent in signed shorts, so split each full-range points value.
    private final int[] data = new int[PER_PAGE * 2 + 5];
    private int review = -1;
    private int page;

    private ShopMenu(int id, Inventory ignored) { this(id, null, List.of(), false); }

    ShopMenu(int id, ServerPlayer owner, List<Settings.Offer> offers, boolean sound) {
        super(TYPE, id);
        this.owner = owner;
        this.offers = offers;
        this.sound = sound;
        for (int i = 0; i < PER_PAGE; i++) {
            addSlot(new Slot(display, i, cardX(i) + 6, cardY(i) + 7));
        }
        for (int i = 0; i < data.length; i++) addDataSlot(DataSlot.shared(data, i));
        if (owner != null) refresh();
    }

    int balance() { return readPoints(PER_PAGE); }
    int price(int i) { return readPoints(i); }
    int selected() { return data[PER_PAGE * 2 + 2] - 1; }
    boolean hasPrevious() { return data[PER_PAGE * 2 + 3] != 0; }
    boolean hasNext() { return data[PER_PAGE * 2 + 4] != 0; }
    private int readPoints(int i) { return ((data[2*i + 1] & 0xffff) << 16) | (data[2*i] & 0xffff); }
    private void writePoints(int i, int value) {
        data[2*i] = (short) value;
        data[2*i + 1] = (short) (value >>> 16);
    }

    @Override public boolean stillValid(Player p) { return p == owner && GiftShop.allowed(owner); }
    @Override public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, net.minecraft.world.inventory.ContainerInput input, Player player) { }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player)) return false;
        if (review >= 0) {
            if (button != BACK && button != BUY) return false;
            var selected = offers.get(page * PER_PAGE + review);
            review = -1; // Consume confirmation even if the purchase fails.
            if (button == BUY) purchase(selected);
        } else if (button == PREVIOUS && page > 0) page--;
        else if (button == NEXT && (page + 1) * PER_PAGE < offers.size()) page++;
        else if (button >= 0 && button < PER_PAGE && page * PER_PAGE + button < offers.size()) {
            if (GiftShop.wallet(owner).points() < offers.get(page * PER_PAGE + button).price()) return false;
            review = button;
        } else return false;
        refresh();
        broadcastChanges();
        return true;
    }

    private void purchase(Settings.Offer offer) {
        var wallet = GiftShop.wallet(owner);
        if (wallet.points() < offer.price()) { GiftShop.say(owner, "Not enough points."); return; }
        var inventory = owner.getInventory();
        var contents = new ItemStack[Inventory.INVENTORY_SIZE];
        for (int i = 0; i < contents.length; i++) contents[i] = inventory.getItem(i).copy();
        var updated = fit(contents, offer.stack());
        if (updated == null) { GiftShop.say(owner, "Make room in your inventory. No points spent."); return; }
        for (int i = 0; i < updated.length; i++) {
            if (!ItemStack.matches(contents[i], updated[i])) inventory.setItem(i, updated[i]);
        }
        inventory.setChanged();
        GiftShop.wallet(owner, wallet.spend(offer.price()));
        Alerts.purchase(owner, sound);
        GiftShop.say(owner, "Bought " + offer.stack().getCount() + " × " + offer.stack().getHoverName().getString() + " for " + offer.price() + " points.");
    }

    static ItemStack[] fit(ItemStack[] contents, ItemStack reward) {
        var next = contents.clone();
        int remaining = reward.getCount();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < next.length && remaining > 0; i++) {
                var old = next[i];
                if (pass == 0 ? old.isEmpty() || !ItemStack.isSameItemSameComponents(old, reward) : !old.isEmpty()) continue;
                int count = old.isEmpty() ? 0 : old.getCount();
                int insert = Math.min(remaining, Math.max(0, reward.getMaxStackSize() - count));
                if (insert > 0) next[i] = reward.copyWithCount(count + insert);
                remaining -= insert;
            }
        }
        return remaining == 0 ? next : null;
    }

    @Override public void broadcastChanges() {
        if (owner != null) writePoints(PER_PAGE, GiftShop.wallet(owner).points());
        super.broadcastChanges();
    }

    private void refresh() {
        writePoints(PER_PAGE, GiftShop.wallet(owner).points());
        data[PER_PAGE * 2 + 2] = review + 1;
        data[PER_PAGE * 2 + 3] = page > 0 ? 1 : 0;
        data[PER_PAGE * 2 + 4] = (page + 1) * PER_PAGE < offers.size() ? 1 : 0;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            var offer = index < offers.size() ? offers.get(index) : null;
            display.setItem(i, offer == null ? ItemStack.EMPTY : offer.stack().copy());
            writePoints(i, offer == null ? 0 : offer.price());
        }
    }
}
