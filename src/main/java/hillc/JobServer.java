/*******************************************************************************
 * Copyright (C) 2018, Christopher Hill <ch6574@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package hillc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Simple socket listening server, that takes in a string payload containing a filename, and periodically watches for
 * that file to appear.
 * <p>
 * Once found it deletes the file and stops watching, replying back to the same socket with a fixed message.
 * <p>
 * Can drive this with all sorts of tools, netcat, telnet, or even pure bash as protocol is very simple where first
 * character in every line data sent back is a magic protocol marker. See PROTO_* statics below for full list.
 * <p>
 * N.B. this only listens on localhost, so the lack of security is intentional!
 */
public class JobServer {

    // Configuration - internal
    private static final int THREADS = Runtime.getRuntime().availableProcessors(); // Number of threads we pool for work
    private static final int WORK_INTERVAL_SECONDS = 30; // Interval for re-scheduling tasks
    private static final int STATS_INTERVAL_SECONDS = 30; // Interval for queue stats logging

    // Configuration - client needs to know these
    private static final int PORT = 12345; // localhost port we listen on

    // Thread safe global things
    private static final Logger LOG = LoggerFactory.getLogger(JobServer.class);
    private static final ScheduledThreadPoolExecutor EXECUTOR_SERVICE = new ScheduledThreadPoolExecutor(THREADS);

    /**
     * Starts the Daemon JobServer on localhost, defaults to port 12345
     *
     * @param args ignored
     * @throws InterruptedException If the server was unable to complete startup/shutdown
     */
    public static void main(String[] args) throws InterruptedException {
        LOG.info("Booting up");

        //
        // Start worker thread pool, with stats logged every n seconds
        //
        LOG.info("Starting worker threads...");
        EXECUTOR_SERVICE.prestartAllCoreThreads();

        new Timer("Worker Stats").scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                LOG.info(EXECUTOR_SERVICE.toString());
            }
        }, 0, STATS_INTERVAL_SECONDS * 1000);

        //
        // Start up the (Netty v4) server to listen on the port, using the Handler below for all incoming traffic
        //
        LOG.info("Starting socket handler...");
        final InetSocketAddress socket = new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT);
        LOG.info("Listening on {}", socket);

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            new ServerBootstrap().group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline().addLast(new Handler());
                    }
                }).childOption(ChannelOption.SO_KEEPALIVE, true)
                // Start accepting connections
                .bind(socket).sync()
                // Also add a graceful shutdown
                .channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * The socket listener logic. Reads the payload and schedules the Runnable "work".
     */
    public static class Handler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            try {
                // Put incoming event onto work queue immediately
                // N.B. Netty has a decode() pattern if we want a POJO here rather than raw bytes.
                EXECUTOR_SERVICE.execute(new JobServerRunnable(EXECUTOR_SERVICE,
                    new JobServerClientOutput(ctx),
                    new JobServerMyBizLogic(WORK_INTERVAL_SECONDS, ((ByteBuf) msg).toString(CharsetUtil.UTF_8))));
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }

}
