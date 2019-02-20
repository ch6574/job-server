package hillc;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import net.jcip.annotations.NotThreadSafe;

import java.io.OutputStream;
import java.util.Objects;

/**
 * Writes out to client via a (not thread safe) OutputStream.
 */
@NotThreadSafe
class JobServerClientOutput {

    /**
     * The wire protocol this server talks back to clients
     */
    static class Protocol {
        // ASCII protocol prefixes
        // Protocol is simply <start of line><RS><control char><... body><\n>
        // e.g. a log line would be     "_Lmy log text\n" where _ is ascii 30 and \n is a newline
        // e.g. a control line would be "_CDONE!0\n"      where _ is ascii 30 and \n is a newline
        static final char RECORD_SEPARATOR = 30;
        static final String PROTO_LOG = RECORD_SEPARATOR + "L";
        static final String PROTO_CTRL = RECORD_SEPARATOR + "C";
        static final char LINE_ENDING = '\n';

        static final String REPLY_DONE = "DONE!"; // Client response once work is done
        static final String REPLY_FAIL = "FAIL!"; // Client response should work fail!
    }

    private final ChannelHandlerContext ctx;
    private final OutputStream os; // This is not thread-safe, so don't share it!

    /**
     * Constructor
     *
     * @param ctx The client's socket channel handler context
     */
    JobServerClientOutput(final ChannelHandlerContext ctx) {
        this.ctx = Objects.requireNonNull(ctx);

        // Dedicated OutputStream connected to the client socket
        this.os = new OutputStream() {
            @Override
            public void write(int i) {
                ctx.write(Unpooled.buffer(Integer.BYTES).writeByte(i));
            }

            @Override
            public void write(byte[] b) {
                ctx.write(Unpooled.wrappedBuffer(b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                ctx.write(Unpooled.wrappedBuffer(b, off, len));
            }

            @Override
            public void flush() {
                ctx.flush();
            }

            @Override
            public void close() {
                // no-op, we leave the underlying socket open even when closing the logging
                // note that swapping appenders triggers a close() on the stream - which can kill buffered wrappers!
            }
        };
    }

    /**
     * @return the client's OutputStream
     */
    OutputStream getOs() {
        return os;
    }

    /**
     * @return if the client is still connected
     */
    boolean isClientConnected() {
        return ctx.channel().isOpen();
    }

    /**
     * Sends the terminal "DONE!" event to the client and closes the socket
     *
     * @param returnCode The return code to be passed back to the client
     */
    void sendDone(int returnCode) {
        ctx.writeAndFlush(Unpooled.copiedBuffer(Protocol.PROTO_CTRL + Protocol.REPLY_DONE + returnCode + Protocol.LINE_ENDING, CharsetUtil.UTF_8))
            .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sends the terminal "FAIL!" event to the client and closes the socket
     *
     * @param returnCode The return code to be passed back to the client
     */
    void sendFail(int returnCode) {
        ctx.writeAndFlush(Unpooled.copiedBuffer(Protocol.PROTO_CTRL + Protocol.REPLY_FAIL + returnCode + Protocol.LINE_ENDING, CharsetUtil.UTF_8))
            .addListener(ChannelFutureListener.CLOSE);
    }
}
