package com.anchorauto;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ablauf (Tick-Zustandsmaschine, ohne jede Bildschirmanzeige):
 * Anchor platzieren -> mit Glowstone laden -> mit Totem in der Hand rechtsklicken (Explosion).
 * Es werden nur normale Vanilla-Pakete gesendet (Slot-Wechsel + Block-Interaktion).
 */
public final class AnchorInteraction {
    private enum Phase { IDLE, PLACE, WAIT_ANCHOR, CHARGE, WAIT_CHARGE, EXPLODE }

    private static final int MAX_WAIT_TICKS = 10;

    private Phase phase = Phase.IDLE;
    private int timer;
    private int originalSlot;
    private int clicksLeft;
    private int lastCharge;
    private int waitTicks;
    private BlockPos anchorPos;
    private BlockHitResult baseHit;

    public void start(MinecraftClient client) {
        if (phase != Phase.IDLE) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null
                || client.currentScreen != null || player.isSneaking()) {
            return;
        }
        if (client.world.getRegistryKey() == World.NETHER) {
            return; // im Nether explodiert der Anker nicht
        }
        AnchorAutoConfig cfg = AnchorAutoConfig.get();

        int selected = player.getInventory().getSelectedSlot();
        if (findTotemSlot(player, selected) < 0) {
            return;
        }
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos hitPos = hit.getBlockPos();
        BlockState hitState = client.world.getBlockState(hitPos);
        boolean needPlace;
        boolean needCharge;

        if (hitState.isOf(Blocks.RESPAWN_ANCHOR)) {
            anchorPos = hitPos;
            needPlace = false;
            needCharge = hitState.get(RespawnAnchorBlock.CHARGES) == 0;
        } else {
            if (findSlot(player, Items.RESPAWN_ANCHOR) < 0) {
                return;
            }
            anchorPos = hitState.isReplaceable() ? hitPos : hitPos.offset(hit.getSide());
            if (!client.world.getBlockState(anchorPos).isReplaceable()) {
                return;
            }
            needPlace = true;
            needCharge = true;
        }
        if (needCharge && findSlot(player, Items.GLOWSTONE) < 0) {
            return;
        }

        baseHit = hit;
        originalSlot = selected;
        lastCharge = -1;
        waitTicks = 0;
        clicksLeft = needCharge
                ? (ThreadLocalRandom.current().nextInt(100) < cfg.doubleGlowstoneChancePercent ? 2 : 1)
                : 0;

        if (needPlace) {
            phase = Phase.PLACE;
            timer = cfg.delayPlaceTicks;
        } else if (needCharge) {
            phase = Phase.CHARGE;
            timer = cfg.delayChargeTicks;
        } else {
            phase = Phase.EXPLODE;
            timer = cfg.delayExplodeTicks;
        }
    }

    public void tick(MinecraftClient client) {
        if (phase == Phase.IDLE) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            reset();
            return;
        }
        if (client.currentScreen != null) {
            abort(client);
            return;
        }
        if (timer > 0 && --timer > 0) {
            return;
        }

        AnchorAutoConfig cfg = AnchorAutoConfig.get();
        switch (phase) {
            case PLACE -> {
                int slot = findSlot(player, Items.RESPAWN_ANCHOR);
                if (slot < 0) {
                    abort(client);
                    return;
                }
                player.getInventory().setSelectedSlot(slot);
                interact(client, player, baseHit);
                phase = Phase.WAIT_ANCHOR;
                waitTicks = 0;
            }
            case WAIT_ANCHOR -> {
                if (isAnchor(client)) {
                    phase = Phase.CHARGE;
                    timer = cfg.delayChargeTicks;
                } else if (++waitTicks > MAX_WAIT_TICKS) {
                    abort(client);
                }
            }
            case CHARGE -> charge(client, player, cfg);
            case WAIT_CHARGE -> {
                if (!isAnchor(client)) {
                    abort(client);
                    return;
                }
                if (charges(client) > lastCharge) {
                    if (clicksLeft > 0) {
                        phase = Phase.CHARGE;
                        timer = cfg.delayChargeTicks;
                    } else {
                        phase = Phase.EXPLODE;
                        timer = cfg.delayExplodeTicks;
                    }
                } else if (++waitTicks > MAX_WAIT_TICKS) {
                    abort(client);
                }
            }
            case EXPLODE -> explode(client, player);
            default -> reset();
        }
    }

    private void charge(MinecraftClient client, ClientPlayerEntity player, AnchorAutoConfig cfg) {
        if (!isAnchor(client) || player.isSneaking()) {
            abort(client);
            return;
        }
        int charges = charges(client);
        if (charges >= RespawnAnchorBlock.MAX_CHARGES || clicksLeft <= 0) {
            phase = Phase.EXPLODE;
            timer = 0;
            return;
        }
        int slot = findSlot(player, Items.GLOWSTONE);
        if (slot < 0) {
            if (charges >= 1) {
                phase = Phase.EXPLODE;
                timer = 0;
            } else {
                abort(client);
            }
            return;
        }
        player.getInventory().setSelectedSlot(slot);
        // Sicherheitscheck: nur mit Glowstone in der Hand laden
        if (!player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            abort(client);
            return;
        }
        interact(client, player, hitFor(client));
        lastCharge = charges;
        clicksLeft--;
        phase = Phase.WAIT_CHARGE;
        waitTicks = 0;
    }

    private void explode(MinecraftClient client, ClientPlayerEntity player) {
        if (!isAnchor(client) || player.isSneaking() || charges(client) < 1
                || client.world.getRegistryKey() == World.NETHER) {
            abort(client);
            return;
        }
        int slot = findTotemSlot(player, originalSlot);
        if (slot < 0) {
            abort(client);
            return;
        }
        player.getInventory().setSelectedSlot(slot);
        if (!player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            abort(client);
            return;
        }
        interact(client, player, hitFor(client));
        player.getInventory().setSelectedSlot(originalSlot);
        reset();
    }

    private void abort(MinecraftClient client) {
        if (client.player != null) {
            client.player.getInventory().setSelectedSlot(originalSlot);
        }
        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        timer = 0;
        anchorPos = null;
        baseHit = null;
        lastCharge = -1;
        waitTicks = 0;
    }

    private void interact(MinecraftClient client, ClientPlayerEntity player, BlockHitResult hit) {
        ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
        }
    }

    /** Echtes Fadenkreuz, falls es auf den Anker zeigt, sonst Ersatz aus dem Start-Treffer. */
    private BlockHitResult hitFor(MinecraftClient client) {
        if (client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(anchorPos)) {
            return hit;
        }
        return new BlockHitResult(baseHit.getPos(), baseHit.getSide(), anchorPos, false);
    }

    private boolean isAnchor(MinecraftClient client) {
        return anchorPos != null && client.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    private int charges(MinecraftClient client) {
        return client.world.getBlockState(anchorPos).get(RespawnAnchorBlock.CHARGES);
    }

    private static int findSlot(ClientPlayerEntity player, Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int findTotemSlot(ClientPlayerEntity player, int preferred) {
        if (player.getInventory().getStack(preferred).isOf(Items.TOTEM_OF_UNDYING)) {
            return preferred;
        }
        return findSlot(player, Items.TOTEM_OF_UNDYING);
    }
}
