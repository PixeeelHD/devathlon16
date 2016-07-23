package net.laby.daemon;

import io.netty.bootstrap.Bootstrap;
import io.netty.util.internal.PlatformDependent;
import lombok.Getter;
import lombok.Setter;
import net.laby.daemon.handler.DisconnectHandler;
import net.laby.daemon.handler.LoginSuccessfulHanndler;
import net.laby.daemon.handler.ServerRequestHandler;
import net.laby.daemon.handler.ServerShutdownRequestHandler;
import net.laby.daemon.task.PowerUsageTask;
import net.laby.daemon.task.QueueStartTask;
import net.laby.daemon.task.ServerStartTask;
import net.laby.daemon.utils.ConfigManager;
import net.laby.protocol.JabyBootstrap;
import net.laby.protocol.packet.PacketLogin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Class created by qlow | Jan
 */
public class JabyDaemon {

    @Getter
    private static JabyDaemon instance;

    @Getter
    @Setter
    private boolean connected;
    @Getter
    @Setter
    private boolean loggedIn;

    @Getter
    private ConfigManager<DaemonConfig> configManager;
    @Getter
    private File serverTemplateFolder;
    @Getter
    private File serverFolder;
    @Getter
    private String startScriptName;
    @Getter
    private String[] availableTypes;
    @Getter
    private Map<UUID, ServerStartTask> startedServers = new HashMap<>();
    @Getter
    private QueueStartTask queueStartTask;

    public JabyDaemon() {
        instance = this;

        init();
    }

    /**
     * Inits the daemon
     */
    private void init() {
        // Starting new command thread
        JabyBootstrap.getExecutorService().execute( new Runnable() {
            @Override
            public void run() {
                Scanner scanner = new Scanner( System.in );

                String readLine;

                while ( (readLine = scanner.nextLine()) != null ) {
                    if ( readLine.equals( "stop" ) ) {
                        disable();
                    }
                }
            }
        } );

        this.configManager = new ConfigManager<>( new File( "config.json" ), DaemonConfig.class );
        this.serverTemplateFolder = new File( getConfig().getServerTemplateFolder() );
        this.serverFolder = new File( getConfig().getServerFolder() );
        this.startScriptName = getConfig().getStartScriptName() + (PlatformDependent.isWindows() ? ".bat" : ".sh");

        if ( !serverTemplateFolder.exists() ) {
            serverTemplateFolder.mkdirs();
        } else {
            List<String> availableTypes = new ArrayList<>();

            for ( File templateFolder : serverTemplateFolder.listFiles() ) {
                if ( !templateFolder.isDirectory() )
                    continue;

                availableTypes.add( templateFolder.getName() );
            }

            this.availableTypes = ( String[] ) availableTypes.toArray();
        }

        // Creating new server-folder if it doesn't exist
        if ( !serverFolder.exists() ) {
            serverFolder.mkdirs();
        }

        JabyBootstrap.registerHandler(
                DisconnectHandler.class,
                LoginSuccessfulHanndler.class,
                ServerRequestHandler.class,
                ServerShutdownRequestHandler.class );

        JabyBootstrap.getExecutorService().execute( (queueStartTask = new QueueStartTask()) );

        connect();
    }

    /**
     * Connects to the server set in the config
     */
    public void connect() {
        JabyBootstrap.runClientBootstrap( getConfig().getAddress(), getConfig().getPort(), getConfig().getMaxRamUsage(), getConfig().getPassword(),
                PacketLogin.ClientType.DAEMON, new Consumer<Bootstrap>() {
                    @Override
                    public void accept( Bootstrap bootstrap ) {
                        if ( bootstrap == null ) {
                            JabyDaemon.this.connected = false;
                            System.err.println( "[Jaby] Failed connecting to " + getConfig().getAddress() + ":" + getConfig().getPort() + "!" );
                            System.out.println( "[Jaby] Attempting connecting again in 10 seconds..." );

                            try {
                                Thread.sleep( 10000L );
                            } catch ( InterruptedException e ) {
                                e.printStackTrace();
                            }

                            connect();
                            return;
                        }


                        JabyDaemon.this.connected = true;
                        System.out.println( "[Jaby] Connected to " + getConfig().getAddress() + ":" + getConfig().getPort() + "!" );

                        JabyBootstrap.getExecutorService().execute( new PowerUsageTask() );
                    }
                } );
    }

    /**
     * Called when the client gets a disconnect-packet
     */
    public void disable() {

        System.out.println( "[Jaby] Stopping servers (Disabling daemon in 1 minute)..." );

        for ( Map.Entry<UUID, ServerStartTask> serverEntry : startedServers.entrySet() ) {
            Process process = serverEntry.getValue().getProcess();

            try {
                process.getOutputStream().write( "stop\n".getBytes() );
                process.getOutputStream().flush();
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }

        // Disabling servers after 1 minute
        JabyBootstrap.getExecutorService().execute( new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep( 60000 );
                } catch ( InterruptedException e ) {
                    e.printStackTrace();
                }

                for ( Map.Entry<UUID, ServerStartTask> serverEntry : startedServers.entrySet() ) {
                    Process process = serverEntry.getValue().getProcess();

                    process.destroy();
                }

                System.out.println( "[Jaby] Disabled daemon!" );
                System.exit( 0 );
            }
        } );
    }

    public DaemonConfig getConfig() {
        return configManager.getSettings();
    }

    public static void main( String[] args ) {
        new JabyDaemon();
    }

}