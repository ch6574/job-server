/*******************************************************************************
 * Copyright (c) 2018, Christopher Hill <ch6574@gmail.com>
 * GNU General Public License v3.0+ (see https://www.gnu.org/licenses/gpl-3.0.txt)
 * SPDX-License-Identifier: GPL-3.0-or-later
 ******************************************************************************/
package hillc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Simple socket listening server, that takes in a string payload and passes that to a JobServerWorker for execution via
 * a ScheduledExecutorService. The Worker is expected to do something (normally short lived) and then "go to sleep"
 * before repeating. The sleeping is implemented by re-scheduling the Worker back in the ScheduledExecutorService with a
 * delay.
 * <p>
 * (In this demo, the client payload is assumed to be a local filename, and the Worker periodically watches for that
 * file to appear, once found it deletes the file and stops watching, replying back to the client on the same socket
 * with fixed messages.)
 * <p>
 * N.B. One can drive this server with all sorts of tools, netcat, telnet, or even pure bash as protocol is very simple
 * with the first character in every line of data sent back is a magic protocol marker. See PROTO_* statics in
 * JobServerClientOutput for full list.
 * <p>
 * N.N.B. this only listens on localhost, so the lack of security is intentional! :-)
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
     * @param args (ignored)
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

                // Peep at head of queue, and see if it has "fallen behind" due to us getting overloaded
                final Runnable head = EXECUTOR_SERVICE.getQueue().peek();
                if (head instanceof ScheduledFuture) {
                    final long delay = ((ScheduledFuture<?>) head).getDelay(TimeUnit.SECONDS);
                    if (delay < 0) {
                        LOG.warn("Worker queue head has fallen {} seconds behind!", -delay);
                    }
                }
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
                EXECUTOR_SERVICE.execute(
                        new JobServerRunnable(EXECUTOR_SERVICE,
                                new JobServerClientOutput(ctx),
                                new JobServerWorkerMyBizLogic(WORK_INTERVAL_SECONDS, ((ByteBuf) msg).toString(CharsetUtil.UTF_8))));
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }

}
