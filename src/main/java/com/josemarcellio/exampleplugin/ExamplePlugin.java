package com.josemarcellio.exampleplugin;

import com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource;
import com.destroystokyo.paper.event.brigadier.CommandRegisteredEvent;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R2.CraftServer;
import org.bukkit.craftbukkit.v1_20_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

import static net.minecraft.commands.Commands.literal;

@DefaultQualifier(NonNull.class)
public final class ExamplePlugin extends JavaPlugin implements Listener {
  @Override
  public void onEnable() {
    this.getServer().getPluginManager().registerEvents(this, this);

    this.registerPluginBrigadierCommand(
      "exampleplugin",
      literal -> literal.requires(sender -> sender.getBukkitSender().hasPermission("exampleplugin.admin"))
        .executes(sender -> {
          sender.getSource().getBukkitSender().sendMessage(createMiniMessages("<#c7f9ff>ExamplePlugin by <#737dde>JoseMarcellio"));
          sender.getSource().getBukkitSender().sendMessage(createMiniMessages("<#c8c7ff>https://github.com/josemarcellio/ExamplePlugin"));
          return Command.SINGLE_SUCCESS;
        })

        .then(literal("createpig")
          .executes(sender -> {
            Player player = (Player) sender.getSource().getBukkitSender();
            Location location = player.getLocation();
            CraftPlayer craftPlayer = (CraftPlayer) player;
            ServerGamePacketListenerImpl connection = craftPlayer.getHandle().connection;
            ServerLevel serverLevel = ((CraftWorld) location.getWorld()).getHandle();
            Pig pig = new Pig(EntityType.PIG, serverLevel);

            pig.setPos(location.getX(), location.getY(), location.getZ());

            connection.send(new ClientboundAddEntityPacket(pig));
            connection.send(new ClientboundSetEntityDataPacket(pig.getId(), Objects.requireNonNull(pig.getEntityData().getNonDefaultValues())));

            player.sendMessage(createMiniMessages("<gradient:yellow:white>Spawning a " + pig.getBukkitEntity().getName() + "<gradient/>"));
            return Command.SINGLE_SUCCESS;
          }))

        .then(literal("createhuman")
          .executes(sender -> {
            Player player = (Player) sender.getSource().getBukkitSender();
            Location location = player.getLocation();
            CraftPlayer craftPlayer = (CraftPlayer) player;
            ServerGamePacketListenerImpl connection = craftPlayer.getHandle().connection;
            MinecraftServer minecraftServer = ((CraftServer) Bukkit.getServer()).getServer();
            ServerLevel serverLevel = ((CraftWorld) location.getWorld()).getHandle();
            ServerPlayer serverPlayer = new ServerPlayer(minecraftServer, serverLevel, new GameProfile(UUID.randomUUID(), "TestHuman"), ClientInformation.createDefault());
            serverPlayer.setPos(location.getX(), location.getY(), location.getZ());

            SynchedEntityData synchedEntityData = serverPlayer.getEntityData();
            synchedEntityData.set(new EntityDataAccessor<>(17, EntityDataSerializers.BYTE), (byte) 127);

            setValue(serverPlayer, "c", connection);

            connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, serverPlayer));
            connection.send(new ClientboundAddEntityPacket(serverPlayer));
            connection.send(new ClientboundSetEntityDataPacket(serverPlayer.getId(), Objects.requireNonNull(synchedEntityData.getNonDefaultValues())));

            player.sendMessage(createMiniMessages("<gradient:red:yellow>Spawning a " + serverPlayer.getGameProfile().getName() + "<gradient/>"));
            return Command.SINGLE_SUCCESS;
          }))
    );
  }

  private PluginBrigadierCommand registerPluginBrigadierCommand(final String label, final Consumer<LiteralArgumentBuilder<CommandSourceStack>> command) {
    final PluginBrigadierCommand pluginBrigadierCommand = new PluginBrigadierCommand(this, label, command);
    this.getServer().getCommandMap().register(this.getName(), pluginBrigadierCommand);
    ((CraftServer) this.getServer()).syncCommands();
    return pluginBrigadierCommand;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @EventHandler
  public void onCommandRegistered(final CommandRegisteredEvent<BukkitBrigadierCommandSource> event) {
    if (!(event.getCommand() instanceof PluginBrigadierCommand pluginBrigadierCommand)) {
      return;
    }
    final LiteralArgumentBuilder<CommandSourceStack> node = literal(event.getCommandLabel());
    pluginBrigadierCommand.command().accept(node);
    event.setLiteral((LiteralCommandNode) node.build());
  }

  public void setValue(Object packet, String fieldName, Object value) {
    try {
      Field field = packet.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(packet, value);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  public Component createMiniMessages (String string) {
    MiniMessage miniMessage = MiniMessage.miniMessage();
    return miniMessage.deserialize(string);
  }
}
