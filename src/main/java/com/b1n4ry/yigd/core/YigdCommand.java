package com.b1n4ry.yigd.core;

import com.b1n4ry.yigd.Yigd;
import com.b1n4ry.yigd.api.YigdApi;
import com.b1n4ry.yigd.config.YigdConfig;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class YigdCommand {
    public static void registerCommands() {
        YigdConfig.CommandToggles config = YigdConfig.getConfig().commandToggles;

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            dispatcher.register(literal("yigd")
                    .then(literal("restore")
                            .requires(source -> source.hasPermissionLevel(4) && config.retrieveGrave)
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> restoreGrave(EntityArgumentType.getPlayer(ctx, "player")))
                            )
                            .executes(ctx -> restoreGrave(ctx.getSource().getPlayer()))
                    )
                    .then(literal("rob")
                            .requires(source -> source.hasPermissionLevel(4) && config.robGrave)
                            .then(argument("victim", EntityArgumentType.player())
                                    .executes(ctx -> robGrave(EntityArgumentType.getPlayer(ctx, "victim"), ctx.getSource().getPlayer()))
                            )
                    )
                    .then(literal("grave")
                            .requires(source -> config.selfView || source.hasPermissionLevel(4))
                            .executes(ctx -> viewGrave(ctx.getSource().getPlayer(), ctx.getSource().getPlayer()))
                            .then(argument("player", EntityArgumentType.player())
                                    .requires(source -> source.hasPermissionLevel(4) && config.adminView)
                                    .executes(ctx -> viewGrave(EntityArgumentType.getPlayer(ctx, "player"), ctx.getSource().getPlayer()))
                            )
                    )
                    .then(literal("moderate")
                            .requires(source -> source.hasPermissionLevel(4) && config.moderateGraves)
                            .executes(ctx -> moderateGraves(ctx.getSource().getPlayer()))
                    )
            );
        });
    }

    private static int moderateGraves(ServerPlayerEntity player) {
        boolean existsGraves = false;
        for (List<DeadPlayerData> data : DeathInfoManager.INSTANCE.data.values()) {
            if (data.size() > 0) {
                existsGraves = true;
                break;
            }
        }
        if (existsGraves) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(DeathInfoManager.INSTANCE.data.size());
            DeathInfoManager.INSTANCE.data.forEach((uuid, deadPlayerData) -> {
                buf.writeUuid(uuid);
                buf.writeInt(deadPlayerData.size());
                for (DeadPlayerData data : deadPlayerData) {
                    buf.writeNbt(data.toNbt());
                }
            });

            ServerPlayNetworking.send(player, new Identifier("yigd", "all_dead_people"), buf);
        } else {
            player.sendMessage(Text.of("No graves found"), false);
            return 0;
        }
        return 1;
    }

    private static int viewGrave(PlayerEntity player, PlayerEntity commandUser) {
        UUID userId = player.getUuid();
        if (commandUser instanceof ServerPlayerEntity spe && DeathInfoManager.INSTANCE.data.containsKey(userId) && DeathInfoManager.INSTANCE.data.get(userId).size() > 0) {
            List<DeadPlayerData> deadPlayerData = DeathInfoManager.INSTANCE.data.get(userId);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(deadPlayerData.size());
            for (DeadPlayerData data : deadPlayerData) {
                buf.writeNbt(data.toNbt());
            }
            ServerPlayNetworking.send(spe, new Identifier("yigd", "single_dead_guy"), buf);
            System.out.println("[YIGD] Sending packet with grave info");
        } else {
            commandUser.sendMessage(new LiteralText(player.getDisplayName().asString() + " does not have any registered graves").styled(style -> style.withColor(0xFF0000)), false);
            return 0;
        }
        return 1;
    }

    // Get world reference variable from registry key identifier
    private static ServerWorld worldFromId (@Nullable MinecraftServer server, Identifier worldId) {
        ServerWorld world = null;
        if (server != null) {
            for (ServerWorld serverWorld : server.getWorlds()) {
                world = serverWorld;
                if (world.getRegistryKey().getValue() == worldId) break;
            }
        }

        return world;
    }

    private static int robGrave(PlayerEntity victim, PlayerEntity stealer) {
        UUID userId = victim.getUuid();

        if (!DeathInfoManager.INSTANCE.data.containsKey(userId)) {
            stealer.sendMessage(Text.of("Could not find grave to fetch"), true);
            return 0;
        }
        List<DeadPlayerData> deadPlayerData = DeathInfoManager.INSTANCE.data.get(userId);

        if (deadPlayerData.size() <= 0) {
            stealer.sendMessage(new LiteralText(victim.getDisplayName().asString() + " does not have any unclaimed graves").styled(style -> style.withColor(0xFF0000)), true);
            return 0;
        }
        DeadPlayerData latestDeath = deadPlayerData.remove(deadPlayerData.size() - 1);
        DeathInfoManager.INSTANCE.markDirty();

        Map<String, Object> modInv = new HashMap<>();
        for (int i = 0; i < Yigd.apiMods.size(); i++) {
            YigdApi yigdApi = Yigd.apiMods.get(i);
            modInv.put(yigdApi.getModName(), latestDeath.modInventories.get(i));
        }

        ServerWorld world = worldFromId(stealer.getServer(), latestDeath.worldId);

        if (world != null && latestDeath.gravePos != null && !world.getBlockState(latestDeath.gravePos).getBlock().equals(Yigd.GRAVE_BLOCK)) {
            world.removeBlock(latestDeath.gravePos, false);
            if (YigdConfig.getConfig().graveSettings.dropGraveBlock) {
                ItemScatterer.spawn(world, latestDeath.gravePos.getX(), latestDeath.gravePos.getY(), latestDeath.gravePos.getZ(), Yigd.GRAVE_BLOCK.asItem().getDefaultStack());
            }
        }

        GraveHelper.RetrieveItems(stealer, latestDeath.inventory, modInv, latestDeath.xp, true);

        stealer.sendMessage(Text.of("Robbed grave remotely successfully"), true);
        victim.sendMessage(Text.of("A server OP has robbed your grave"), false);
        return 1;
    }

    private static int restoreGrave(PlayerEntity player) {
        UUID userId = player.getUuid();

        if (!DeathInfoManager.INSTANCE.data.containsKey(userId)) {
            player.sendMessage(Text.of("Could not find grave to fetch"), true);
            return -1;
        }
        List<DeadPlayerData> deadPlayerData = DeathInfoManager.INSTANCE.data.get(userId);

        if (deadPlayerData.size() <= 0) {
            player.sendMessage(new LiteralText(player.getDisplayName().asString() + " does not have any unclaimed graves").styled(style -> style.withColor(0xFF0000)), false);
            return -1;
        }

        DeadPlayerData latestDeath = deadPlayerData.remove(deadPlayerData.size() - 1);
        DeathInfoManager.INSTANCE.markDirty();

        Map<String, Object> modInv = new HashMap<>();
        for (int i = 0; i < Yigd.apiMods.size(); i++) {
            YigdApi yigdApi = Yigd.apiMods.get(i);
            modInv.put(yigdApi.getModName(), latestDeath.modInventories.get(i));
        }

        ServerWorld world = worldFromId(player.getServer(), latestDeath.worldId);

        if (world != null && latestDeath.gravePos != null && !world.getBlockState(latestDeath.gravePos).getBlock().equals(Yigd.GRAVE_BLOCK)) {
            world.removeBlock(latestDeath.gravePos, false);
            if (YigdConfig.getConfig().graveSettings.dropGraveBlock) {
                ItemScatterer.spawn(world, latestDeath.gravePos.getX(), latestDeath.gravePos.getY(), latestDeath.gravePos.getZ(), Yigd.GRAVE_BLOCK.asItem().getDefaultStack());
            }
        }

        GraveHelper.RetrieveItems(player, latestDeath.inventory, modInv, latestDeath.xp, false);

        player.sendMessage(Text.of("Restored grave remotely successfully"), true);
        return 1;
    }
}
