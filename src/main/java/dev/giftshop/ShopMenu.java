package dev.giftshop;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;

final class ShopMenu extends ChestMenu {
    private static final int PER_PAGE = 18;
    private final ServerPlayer owner;
    private final List<Settings.Offer> offers;
    private Settings.Offer review;
    private int page;
    private int displayedPoints = -1;

    ShopMenu(int id, ServerPlayer owner, List<Settings.Offer> offers) {
        super(MenuType.GENERIC_9x3, id, owner.getInventory(), new SimpleContainer(27), 3);
        this.owner = owner;
        this.offers = offers;
        render();
    }

    @Override public boolean stillValid(Player p) { return p == owner && GiftShop.allowed(owner); }
    @Override public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        // All slots are read-only in this screen, including the displayed player inventory.
        if (!stillValid(player) || input != ContainerInput.PICKUP || button != 0 || slot < 0 || slot >= 27) return;
        if (review != null) {
            if (slot == 18) review = null;
            else if (slot == 22) {
                var selected = review;
                review = null; // consume confirmation before validating or delivering
                purchase(selected);
            } else return;
        } else if (slot == 18 && page > 0) page--;
        else if (slot == 26 && (page + 1) * PER_PAGE < offers.size()) page++;
        else if (slot < PER_PAGE && page * PER_PAGE + slot < offers.size()) {
            var selected = offers.get(page * PER_PAGE + slot);
            if (GiftShop.wallet(owner).points() >= selected.price()) review = selected;
            else GiftShop.say(owner, "Not enough points.");
        } else return;
        render();
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
        if (GiftShop.wallet(owner).points() != displayedPoints) render();
        super.broadcastChanges();
    }

    private void render() {
        var grid = getContainer();
        grid.clearContent();
        displayedPoints = GiftShop.wallet(owner).points();
        if (review != null) {
            grid.setItem(4, preview(review, "Balance after purchase: " + Math.max(0, displayedPoints - review.price())));
            grid.setItem(18, icon(new ItemStack(Items.BARRIER), "Cancel", "No points spent"));
            grid.setItem(22, icon(new ItemStack(Items.EMERALD), "Buy for " + review.price() + " points", "Click to confirm"));
        } else {
            for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < offers.size(); i++) {
                var offer = offers.get(page * PER_PAGE + i);
                grid.setItem(i, preview(offer, displayedPoints < offer.price() ? "Need " + (offer.price() - displayedPoints) + " more points" : "Click to review"));
            }
            if (page > 0) grid.setItem(18, icon(new ItemStack(Items.ARROW), "Previous page", "Click to go back"));
            if ((page + 1) * PER_PAGE < offers.size()) grid.setItem(26, icon(new ItemStack(Items.ARROW), "Next page", "Click to continue"));
        }
        grid.setItem(23, icon(new ItemStack(Items.SUNFLOWER), "Balance: " + displayedPoints + " points", "Points are saved with this world"));
    }

    private static ItemStack preview(Settings.Offer offer, String cue) {
        var reward = offer.stack();
        return icon(reward.copy(), reward.getCount() + " × " + reward.getHoverName().getString(), "Price: " + offer.price() + " points", cue);
    }
    private static ItemStack icon(ItemStack stack, String name, String... lines) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(lines)
            .<Component>map(l -> Component.literal(l).withStyle(ChatFormatting.YELLOW)).toList()));
        return stack;
    }
}
