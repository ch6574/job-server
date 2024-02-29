/*******************************************************************************
 * Copyright (c) 2018, Christopher Hill <ch6574@gmail.com>
 * GNU General Public License v3.0+ (see https://www.gnu.org/licenses/gpl-3.0.txt)
 * SPDX-License-Identifier: GPL-3.0-or-later
 ******************************************************************************/
package hillc;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import hillc.JobServerClientOutput.Protocol;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The runnable object that lives in the ExecutorService's work queue. Client output and Worker logic provided at
 * construct time.
 * <p>
 * N.B. Logback specific implementation.
 */
@ThreadSafe
class JobServerRunnable implements Runnable {

    // Thread safe global things
    private static final Logger LOG = LoggerFactory.getLogger(JobServerRunnable.class);
    private static final LoggerContext LOGGER_CONTEXT = (LoggerContext) LoggerFactory.getILoggerFactory();
    private static final PatternLayoutEncoder ENCODER;

    static {
        // Client logging that prefixes each line with PROTO_LOG so that the client can decode
        ENCODER = new PatternLayoutEncoder();
        ENCODER.setPattern(Protocol.PROTO_LOG + "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level - %msg%n");
        ENCODER.setContext(LOGGER_CONTEXT);
        ENCODER.start();
    }

    //
    // Local logger that writes to the open socket (so the client can record log events) *and* our main log
    //
    // N.B. This is a local variable as each time run() is called a different thread could be executing us, so
    // we (re)associate the thread's specific Logger+Appender with this instance's OutputStream upon each entry.
    // That way we avoid creating expensive Logger related artifacts, that never garbage collect, and instead
    // multiplex them each time
    //
    private static final ThreadLocal<ch.qos.logback.classic.Logger> CLIENT_LOGGER = ThreadLocal.withInitial(() -> {
        // Create the appender
        final OutputStreamAppender<ILoggingEvent> clientAppender = new OutputStreamAppender<>();
        clientAppender.setName(localName()); // Name it, so we can recall in run()
        clientAppender.setEncoder(ENCODER);
        clientAppender.setOutputStream(new OutputStream() {
            @Override
            public void write(final int i) {
            } // Temporarily hook to thin air to enable initial startup
        });
        clientAppender.setContext(LOGGER_CONTEXT);
        clientAppender.start();

        final ch.qos.logback.classic.Logger logger = LOGGER_CONTEXT.getLogger(localName());
        logger.addAppender(clientAppender);

        return logger;
    });

    // Instance specific local variables
    private final ScheduledExecutorService scheduledThreadPoolExecutor;
    private final JobServerClientOutput jobServerClientOutput;
    private final JobServerWorker jobServerWorker;
    private ScheduledFuture scheduledFuture = null;

    /**
     * Constructor
     *
     * @param scheduledThreadPoolExecutor The thread pool scheduler
     * @param jobServerClientOutput       The client output processor
     * @param jobServerWorker             the provided worker logic
     */
    JobServerRunnable(final ScheduledExecutorService scheduledThreadPoolExecutor,
                      final JobServerClientOutput jobServerClientOutput,
                      final JobServerWorker jobServerWorker) {
        this.scheduledThreadPoolExecutor = Objects.requireNonNull(scheduledThreadPoolExecutor);
        this.jobServerClientOutput = Objects.requireNonNull(jobServerClientOutput);
        this.jobServerWorker = Objects.requireNonNull(jobServerWorker);
    }

    // Helper to return thread local name, used when getting logger components by name
    private static String localName() {
        return JobServerRunnable.class + Thread.currentThread().getName();
    }

    @Override
    public void run() {
        // First do a sanity check if the client is still there
        if (!jobServerClientOutput.isClientConnected()) {
            LOG.info("Client gone, abandoning work for: {}", jobServerWorker.getName());
            return;
        }

        // (Re)attach this thread's special Logger+Appender to our instance's OutputStream
        // N.B. No need to buffer this as OutputStreamAppender flushes whole lines at once
        final ch.qos.logback.classic.Logger clientLog = CLIENT_LOGGER.get();
        ((OutputStreamAppender<?>) clientLog.getAppender(localName())).setOutputStream(jobServerClientOutput.getOs());

        // Now do the worker logic
        try {
            if (jobServerWorker.doWork(clientLog)) {
                // All done! Notify the client and close the socket
                jobServerClientOutput.sendDone(jobServerWorker.getReturnCode());
            } else {
                // Reschedule ourselves again in future, ensure this is the last thing we do else we need Worker thread safety
                scheduledFuture = scheduledThreadPoolExecutor.schedule(this, jobServerWorker.getRescheduleInterval(), TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOG.error("Problem with processing, failing for: {}", jobServerWorker.getName(), e);

            // Notify the client, and close the socket
            jobServerClientOutput.sendFail(jobServerWorker.getReturnCode());
        }
    }

    /**
     * @return If we have been scheduled, the Future associated with it, else null
     */
    public ScheduledFuture getScheduledFuture() {
        return scheduledFuture;
    }
}
