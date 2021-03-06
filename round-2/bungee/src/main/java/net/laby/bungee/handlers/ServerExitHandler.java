package net.laby.bungee.handlers;

import io.netty.channel.ChannelHandlerContext;
import net.laby.bungee.ApplicationUpdater;
import net.laby.bungee.ServerType;
import net.laby.protocol.packet.PacketExitServer;
import net.md_5.bungee.api.ProxyServer;

/**
 * Class created by qlow | Jan
 */
public class ServerExitHandler {

    public static void handle( PacketExitServer exitServer, ChannelHandlerContext ctx ) {
        // Removing server
        ServerType serverType = ServerType.getByName( exitServer.getType() );

        ProxyServer.getInstance().getServers().remove( serverType.getType() + "-"
                + (exitServer.getPort() % 40000) );

        if ( serverType.getServers().containsKey( exitServer.getUuid() ) ) {
            // Removing server from bungeecord
            serverType.getServers().remove( exitServer.getUuid() );
        }

        // Log message
        System.out.println( "[Jaby] Server " + exitServer.getUuid().toString() + " (" +
                serverType.getType() + ") stopped!" );

        // Calling update
        ApplicationUpdater.updateTypes();
    }

}
