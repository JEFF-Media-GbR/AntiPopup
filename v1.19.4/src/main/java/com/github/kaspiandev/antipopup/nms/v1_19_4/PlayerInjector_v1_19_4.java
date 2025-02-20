package com.github.kaspiandev.antipopup.nms.v1_19_4;

import com.github.kaspiandev.antipopup.nms.AbstractInjector;
import io.netty.channel.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.Optional;

public class PlayerInjector_v1_19_4 implements AbstractInjector {

    static {
        // https://nms.screamingsandals.org/1.19.4/net/minecraft/server/network/ServerGamePacketListenerImpl.html
        // Field "PlayerConnection.h" (ServerGamePacketListenerImpl.connection in mojang maps) is not public in 1.19.4
        try {
            // This should work on all versions, no matter how the field is called
            for (Field field : ServerGamePacketListenerImpl.class.getDeclaredFields()) {
                if (field.getType().equals(Connection.class)) {
                    field.setAccessible(true);
                    break;
                }
            }
        } catch (SecurityException | InaccessibleObjectException exception) {
            throw new RuntimeException("Could not make \"connection\" field accessible", exception);
        }
    }

    public void inject(Player player) {
        ChannelDuplexHandler duplexHandler = new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
                if (packet instanceof ClientboundPlayerChatPacket chatPacket) {
                    Component content = chatPacket.unsignedContent();
                    if (content == null) {
                        content = Component.literal(chatPacket.body().content());
                    }
                    Optional<ChatType.Bound> chatType = chatPacket.chatType().resolve(
                            ((CraftServer) Bukkit.getServer()).getServer().registryAccess());

                    ((CraftPlayer) player).getHandle().connection.send(
                            new ClientboundSystemChatPacket(chatType.orElseThrow().decorate(content), false));
                    return;
                }
                super.write(ctx, packet, promise);
            }
        };
        Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
        channel.pipeline().addBefore("packet_handler", "antipopup_handler", duplexHandler);
    }

    public void uninject(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
        channel.eventLoop().submit(() -> {
            channel.pipeline().remove(player.getName());
            return null;
        });
    }
}