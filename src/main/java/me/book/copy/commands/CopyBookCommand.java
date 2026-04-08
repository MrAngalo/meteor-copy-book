package me.book.copy.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.book.copy.mixin.PlayerInventoryAccessor;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class CopyBookCommand extends Command {
    public CopyBookCommand() {
        super("copybook", "Copies a book's pages to empty Books and Quills in your inventory.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("copies", IntegerArgumentType.integer(1))
            .executes(context -> {
                int copies = IntegerArgumentType.getInteger(context, "copies");
                ItemStack held = mc.player.getMainHandStack();
                if (!held.isOf(Items.WRITTEN_BOOK)) {
                    error("Hold a Written Book in your main hand, or specify a title.");
                    return SINGLE_SUCCESS;
                }
                WrittenBookContentComponent content = held.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                if (content == null) {
                    error("Held book has no content.");
                    return SINGLE_SUCCESS;
                }
                return execute(copies, content.title().raw(), held);
            })
            .then(argument("title", StringArgumentType.greedyString())
                .executes(context -> {
                    int copies = IntegerArgumentType.getInteger(context, "copies");
                    String title = StringArgumentType.getString(context, "title");
                    if (title.length() > 15) {
                        error("Title must be 15 characters or less.");
                        return SINGLE_SUCCESS;
                    }
                    return execute(copies, title, mc.player.getMainHandStack());
                })
            )
        );
    }

    private int execute(int copies, String title, ItemStack held) {
        if (!held.isOf(Items.WRITTEN_BOOK) && !held.isOf(Items.WRITABLE_BOOK)) {
            error("Hold a Written Book or Book and Quill in your main hand.");
            return SINGLE_SUCCESS;
        }

        // Extract pages from held book
        final List<String> pages;
        if (held.isOf(Items.WRITTEN_BOOK)) {
            WrittenBookContentComponent content = held.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
            if (content == null || content.pages().isEmpty()) {
                error("Held book has no pages.");
                return SINGLE_SUCCESS;
            }
            pages = content.pages().stream()
                .map(page -> page.raw().getString())
                .collect(Collectors.toList());
        } else {
            WritableBookContentComponent content = held.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
            if (content == null || content.pages().isEmpty()) {
                error("Held book has no pages.");
                return SINGLE_SUCCESS;
            }
            pages = content.pages().stream()
                .map(page -> page.raw())
                .collect(Collectors.toList());
        }

        // Find held slot (hotbar only, since player must hold it)
        int heldSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i) == held) {
                heldSlot = i;
                break;
            }
        }
        final int finalHeldSlot = heldSlot;

        // Scan full inventory (slots 0-35) for empty Books and Quills, excluding held slot
        final List<Integer> bookSlots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (i == heldSlot) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.WRITABLE_BOOK)) continue;
            WritableBookContentComponent content = stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
            if (content != null && !content.pages().isEmpty()) continue;
            bookSlots.add(i);
            if (bookSlots.size() >= copies) break;
        }

        if (bookSlots.size() < copies) {
            error("Not enough empty Books and Quills. Found " + bookSlots.size() + ", need " + copies + ".");
            return SINGLE_SUCCESS;
        }

        // Build hotbar cycle: all slots except the held slot, in order
        final List<Integer> hotbarCycle = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (i != heldSlot) hotbarCycle.add(i);
        }

        final String finalTitle = title;

        info("Copying " + copies + " book(s) with title \"" + title + "\"...");

        final AtomicBoolean shouldStop = new AtomicBoolean(false);

        Thread thread = new Thread((Runnable) () -> {
            for (int i = 0; i < bookSlots.size(); i++) {
                final int invSlot = bookSlots.get(i);
                final boolean inHotbar = invSlot < 9;
                final int copyNumber = i + 1;
                final int targetHotbarSlot = hotbarCycle.get(i % hotbarCycle.size());

                // Step 1: validate, swap to target slot, switch selected slot
                mc.execute(() -> {
                    ItemStack stack = mc.player.getInventory().getStack(invSlot);
                    if (!stack.isOf(Items.WRITABLE_BOOK)) {
                        error("Book and Quill is gone. Stopping.");
                        shouldStop.set(true);
                        return;
                    }
                    WritableBookContentComponent existingContent = stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
                    if (existingContent != null && !existingContent.pages().isEmpty()) {
                        error("Book and Quill is no longer empty. Stopping.");
                        shouldStop.set(true);
                        return;
                    }

                    info(copyNumber + "/" + bookSlots.size());

                    if (invSlot != targetHotbarSlot) {
                        int screenSlot = inHotbar ? 36 + invSlot : invSlot;
                        mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            screenSlot,
                            targetHotbarSlot,
                            SlotActionType.SWAP,
                            mc.player
                        );
                    }

                    ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(targetHotbarSlot);
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(targetHotbarSlot));
                });

                // Step 2: wait 1-2 seconds after slot switch
                try {
                    Thread.sleep(1000 + ThreadLocalRandom.current().nextLong(1001));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (shouldStop.get()) return;

                if (mc.player.isSneaking()) {
                    mc.execute(() -> info("Cancelled."));
                    return;
                }

                // Step 3: sign the book
                mc.execute(() -> {
                    if (shouldStop.get()) return;
                    mc.player.networkHandler.sendPacket(new BookUpdateC2SPacket(
                        targetHotbarSlot,
                        pages,
                        Optional.of(finalTitle)
                    ));
                });

                // Step 4: wait 1-2 seconds before next iteration's slot switch
                if (i < bookSlots.size() - 1) {
                    try {
                        Thread.sleep(1000 + ThreadLocalRandom.current().nextLong(1001));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    if (shouldStop.get()) return;

                    if (mc.player.isSneaking()) {
                        mc.execute(() -> info("Cancelled."));
                        return;
                    }
                }
            }

            mc.execute(() -> {
                if (shouldStop.get()) return;
                ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(finalHeldSlot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(finalHeldSlot));
                info("Done! Signed " + bookSlots.size() + " book(s).");
            });
        });
        thread.setDaemon(true);
        thread.start();

        return SINGLE_SUCCESS;
    }
}
