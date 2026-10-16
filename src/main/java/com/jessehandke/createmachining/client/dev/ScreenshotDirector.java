package com.jessehandke.createmachining.client.dev;

import java.util.ArrayList;
import java.util.List;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.cnc.CncLatheBlockEntity;
import com.jessehandke.createmachining.content.cnc.CncLatheItem;
import com.jessehandke.createmachining.content.cnc.CncLatheLayout;
import com.jessehandke.createmachining.content.cnc.CncLathePart;
import com.jessehandke.createmachining.registry.ModBlocks;
import com.jessehandke.createmachining.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * DEV TOOL, never active in normal play. Launched with ./gradlew runScreenshotClient (which sets
 * the create_machining.screenshot system property), it builds a scene in a test world, runs the
 * CNC Lathe, points the camera at it and saves screenshots to run/screenshots/, then quits.
 * Lets us check how the machine looks without a person at the keyboard.
 */
public final class ScreenshotDirector {
    private ScreenshotDirector() {}

    public static boolean enabled() {
        return Boolean.getBoolean("create_machining.screenshot");
    }

    private static final Direction FRONT = Direction.SOUTH;
    private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);

    /** One step of the script: at `tick`, run `action`. */
    private record Step(int tick, Runnable action) {}

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static int ticks = -1;

    static {
        int t = 60; // give chunks and models a few seconds to settle
        SCRIPT.add(new Step(t, ScreenshotDirector::buildScene));
        t += 60;
        SCRIPT.add(new Step(t, () -> camera(4.2, -57.6, 4.2, 1.5, -59.2, -0.5)));
        t += 30;
        SCRIPT.add(new Step(t, () -> shot("01_front_idle")));
        SCRIPT.add(new Step(t + 3, () -> camera(1.6, -58.4, 2.6, 1.6, -59.0, -0.3)));
        t += 20;
        SCRIPT.add(new Step(t, () -> shot("01b_closeup_idle_door_open")));
        SCRIPT.add(new Step(t + 3, () -> camera(2.6, -58.6, -3.4, 2.0, -59.6, -1.0)));
        t += 20;
        SCRIPT.add(new Step(t, () -> shot("01c_back_tailstock_end")));
        SCRIPT.add(new Step(t + 5, () -> camera(-2.6, -58.0, -4.6, 1.0, -59.5, -0.5)));
        t += 35;
        SCRIPT.add(new Step(t, () -> shot("02_back_drive")));
        SCRIPT.add(new Step(t + 5, ScreenshotDirector::startCycle));
        SCRIPT.add(new Step(t + 10, () -> camera(1.6, -58.4, 2.6, 1.6, -59.0, -0.3)));
        t += 45;
        SCRIPT.add(new Step(t, () -> shot("03_cutting_closeup")));
        SCRIPT.add(new Step(t + 3, () -> camera(1.5, -55.4, 0.9, 1.5, -59.4, -0.4)));
        t += 15;
        SCRIPT.add(new Step(t, () -> shot("04_cutting_top")));
        SCRIPT.add(new Step(t + 3, () -> camera(4.2, -57.6, 4.2, 1.5, -59.2, -0.5)));
        t += 15;
        SCRIPT.add(new Step(t, () -> shot("05_front_cutting")));
        SCRIPT.add(new Step(t + 3, ScreenshotDirector::dropItemIcon));
        SCRIPT.add(new Step(t + 6, () -> camera(4.6, -59.0, 3.4, 4.6, -59.85, 2.5)));
        t += 25;
        SCRIPT.add(new Step(t, () -> shot("06_item_model")));
        SCRIPT.add(new Step(t + 10, () -> Minecraft.getInstance().stop()));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null)
            return;
        mc.options.hideGui = true;
        ticks++;
        for (Step step : SCRIPT)
            if (step.tick() == ticks)
                step.action().run();
    }

    // ---- scene ------------------------------------------------------------------------------

    /** Run something on the integrated server's thread (world changes must happen there). */
    private static void onServer(java.util.function.Consumer<ServerLevel> action) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> action.accept(server.overworld()));
    }

    private static void buildScene() {
        onServer(level -> {
            level.setDayTime(6000);
            level.getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)
                    .set(false, level.getServer());
            BlockPos controller = CncLatheLayout.posOf(CncLathePart.CONTROLLER, ORIGIN, FRONT);
            // Start from a fresh machine every run (the test world is reused): empty it, remove it,
            // sweep up any dropped items, then build a new one
            if (level.getBlockEntity(controller) instanceof CncLatheBlockEntity old) {
                for (var inv : List.of(old.stockInv, old.toolInv, old.partsInv, old.chipInv))
                    for (int i = 0; i < inv.getSlots(); i++)
                        inv.setStackInSlot(i, ItemStack.EMPTY);
                old.conveyorInstalled = false;
            }
            level.setBlock(controller, Blocks.AIR.defaultBlockState(), 3);
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(ORIGIN).inflate(6)).forEach(e -> e.discard());
            CncLatheItem.build(level, ORIGIN, FRONT);
            BlockPos motor = controller.relative(FRONT.getOpposite());
            level.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, FRONT), 3);
            if (level.getBlockEntity(motor) instanceof CreativeMotorBlockEntity m)
                m.generatedSpeed.setValue(16); // slow, so shots land mid-cut
            // a chest at the chip outlet, and the conveyor installed
            BlockPos outlet = CncLatheLayout.posOf(CncLathePart.CHIP_OUTLET, ORIGIN, FRONT);
            level.setBlock(outlet.relative(CncLatheLayout.lengthDir(FRONT)), Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(controller) instanceof CncLatheBlockEntity lathe) {
                lathe.conveyorInstalled = true;
                lathe.toolInv.setStackInSlot(0, new ItemStack(ModItems.CARBIDE_INSERT.get()));
                lathe.sendData();
            }
            CreateMachining.LOGGER.info("[screenshot] scene built");
        });
    }

    private static void startCycle() {
        onServer(level -> {
            BlockPos controller = CncLatheLayout.posOf(CncLathePart.CONTROLLER, ORIGIN, FRONT);
            if (level.getBlockEntity(controller) instanceof CncLatheBlockEntity lathe) {
                lathe.stockInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 16));
                lathe.sendData();
            }
        });
    }

    /** The CNC Lathe item lying on the ground, to check its 1/3 scale inventory model. */
    private static void dropItemIcon() {
        onServer(level -> {
            var item = new net.minecraft.world.entity.item.ItemEntity(level, 4.6, -60, 2.5,
                    new ItemStack(ModItems.CNC_LATHE.get()), 0, 0, 0);
            item.setNeverPickUp();
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
        });
    }

    /** Put the player at `eye` looking at `target`. */
    private static void camera(double ex, double ey, double ez, double tx, double ty, double tz) {
        onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
            player.getAbilities().flying = true;
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            Vec3 d = new Vec3(tx - ex, ty - ey, tz - ez);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            player.teleportTo(level, ex, ey - player.getEyeHeight(), ez, yaw, pitch);
        });
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                msg -> CreateMachining.LOGGER.info("[screenshot] {}", msg.getString()));
    }
}
