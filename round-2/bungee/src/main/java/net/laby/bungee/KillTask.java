package net.laby.bungee;

import net.laby.protocol.packet.PacketRequestShutdown;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Class created by qlow | Jan
 */
public class KillTask implements Runnable {

    public KillTask() {
        ProxyServer.getInstance().getScheduler().schedule( Jaby.getInstance(), this, 5L, 5L, TimeUnit.SECONDS );
    }

    @Override
    public void run() {
        // Iterating through types
        for ( ServerType serverType : ServerType.getServerTypes() ) {
            Map<UUID, JabyServer> servers = serverType.getServers();

            if ( servers.size() == 0 )
                continue;

            // List of uuids of the server that should be removed
            List<UUID> removeAll = new ArrayList<>();

            // Iterating through servers
            for ( Map.Entry<UUID, JabyServer> serverEntry : servers.entrySet() ) {
                String serverName = serverType.getType() + "-" + (serverEntry.getValue().getPort() % 40000);

                if ( !ProxyServer.getInstance().getServers().containsKey( serverName ) )
                    continue;

                // Checking for empty server
                ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo( serverName );

                if ( serverInfo.getPlayers().size() > 0 ) {
                    if ( serverEntry.getValue().getFirstTimeZeroPlayers() != -1 ) {
                        serverEntry.getValue().setFirstTimeZeroPlayers( -1 );
                    }

                    continue;
                }

                if ( serverEntry.getValue().getFirstTimeZeroPlayers() == -1 ) {
                    serverEntry.getValue().setFirstTimeZeroPlayers( System.currentTimeMillis() );
                }

                if ( (System.currentTimeMillis() - serverEntry.getValue().getFirstTimeZeroPlayers())
                        < (serverType.getSecondsUntilStop() * 1000) ) {
                    continue;
                }

                // Killing servers
                System.out.println( "[Jaby] Closing server " + serverEntry.getKey().toString()
                        + " because of inactivity" );

                removeAll.add( serverEntry.getKey() );

                serverEntry.getValue().getChannel().getChannel().writeAndFlush(
                        new PacketRequestShutdown( serverEntry.getKey() ) );
            }

            for ( UUID remove : removeAll ) {
                servers.remove( remove );
            }
        }
    }

}
