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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.io.OutputStream;

import hillc.JobServerClientOutput.Protocol;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class JobServerClientOutputTest {

    // Mocks
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ChannelHandlerContext mockCtx;
    @Mock
    private ChannelFuture mockChannelFuture;

    // Captors
    private final ArgumentCaptor<ByteBuf> bbArgumentCaptor = ArgumentCaptor.forClass(ByteBuf.class);
    private final ArgumentCaptor<ChannelFutureListener> cflArgumentCaptor = ArgumentCaptor.forClass(ChannelFutureListener.class);

    // Test object
    @InjectMocks
    private JobServerClientOutput jobServerClientOutput;

    @Test
    public void testInternalOutputStream() throws IOException {
        // Test this data is faithfully set to the channel buffer via the internal OutputStream
        String testData = "Hello, World!";

        // Given
        OutputStream os = jobServerClientOutput.getOs();

        // Run
        os.write(testData.getBytes());
        os.flush();

        // Verify
        verify(mockCtx, times(1)).write(bbArgumentCaptor.capture());
        verify(mockCtx, times(1)).flush();
        verifyNoMoreInteractions(mockCtx);

        // Assert
        assertEquals(testData, bbArgumentCaptor.getValue().toString(CharsetUtil.UTF_8));
    }

    @Test
    public void testDone() {
        // Test the standard "DONE!" reply, plus the return code, is sent to the channel buffer and closed
        int returnCode = 123;

        // Given
        when(mockCtx.writeAndFlush(any())).thenReturn(mockChannelFuture);

        // Run
        jobServerClientOutput.sendDone(returnCode);

        // Verify
        verify(mockCtx, times(1)).writeAndFlush(bbArgumentCaptor.capture());
        verify(mockChannelFuture, times(1)).addListener(cflArgumentCaptor.capture());
        verifyNoMoreInteractions(mockCtx);
        verifyNoMoreInteractions(mockChannelFuture);

        // Assert
        assertEquals(Protocol.PROTO_CTRL + Protocol.REPLY_DONE + returnCode + Protocol.LINE_ENDING, bbArgumentCaptor.getValue().toString(CharsetUtil.UTF_8));
        assertEquals(ChannelFutureListener.CLOSE, cflArgumentCaptor.getValue());
    }

    @Test
    public void testFail() {
        // Test the standard "FAIL!" reply, plus the return code, is sent to the channel buffer and closed
        int returnCode = 456;

        // Given
        when(mockCtx.writeAndFlush(any())).thenReturn(mockChannelFuture);

        // Run
        jobServerClientOutput.sendFail(returnCode);

        // Verify
        verify(mockCtx, times(1)).writeAndFlush(bbArgumentCaptor.capture());
        verify(mockChannelFuture, times(1)).addListener(cflArgumentCaptor.capture());
        verifyNoMoreInteractions(mockCtx);
        verifyNoMoreInteractions(mockChannelFuture);

        // Assert
        assertEquals(Protocol.PROTO_CTRL + Protocol.REPLY_FAIL + returnCode + Protocol.LINE_ENDING, bbArgumentCaptor.getValue().toString(CharsetUtil.UTF_8));
        assertEquals(ChannelFutureListener.CLOSE, cflArgumentCaptor.getValue());
    }

}
