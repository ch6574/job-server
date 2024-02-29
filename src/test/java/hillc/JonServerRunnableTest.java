/*******************************************************************************
 * Copyright (c) 2018, Christopher Hill <ch6574@gmail.com>
 * GNU General Public License v3.0+ (see https://www.gnu.org/licenses/gpl-3.0.txt)
 * SPDX-License-Identifier: GPL-3.0-or-later
 ******************************************************************************/
package hillc;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.OutputStream;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class JonServerRunnableTest {

    // Mocks
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private ScheduledExecutorService mockScheduledExecutorService;
    @Mock
    private JobServerClientOutput mockJobServerClientOutput;
    @Mock
    private JobServerWorker mockJobServerWorker;
    @Mock
    private OutputStream mockOutputStream;

    // Test object
    @InjectMocks
    private JobServerRunnable jobServerRunnable;

    @Test
    public void testRunToCompletion() throws Exception {
        int returnCode = 123;

        // Given
        when(mockJobServerClientOutput.getOs()).thenReturn(mockOutputStream);
        when(mockJobServerClientOutput.isClientConnected()).thenReturn(true);
        when(mockJobServerWorker.doWork(any())).thenReturn(true); // Simulate completion
        when(mockJobServerWorker.getReturnCode()).thenReturn(returnCode);

        // Run
        jobServerRunnable.run();

        // Verify
        verify(mockJobServerClientOutput, times(1)).getOs();
        verify(mockJobServerClientOutput, times(1)).isClientConnected();
        verify(mockJobServerClientOutput, times(1)).sendDone(returnCode);
        verify(mockJobServerWorker, times(1)).doWork(any());
        verify(mockJobServerWorker, times(1)).getReturnCode();

        verifyNoMoreInteractions(mockJobServerClientOutput);
        verifyNoMoreInteractions(mockJobServerWorker);
        verifyNoInteractions(mockScheduledExecutorService); // No rescheduling
    }

    @Test
    public void testRunToRunAgain() throws Exception {
        // Given
        when(mockJobServerClientOutput.getOs()).thenReturn(mockOutputStream);
        when(mockJobServerClientOutput.isClientConnected()).thenReturn(true);
        when(mockJobServerWorker.doWork(any())).thenReturn(false); // Simulate incomplete
        when(mockJobServerWorker.getRescheduleInterval()).thenReturn(5); // 5 sec delay

        // Run
        jobServerRunnable.run();

        // Verify
        verify(mockJobServerClientOutput, times(1)).getOs();
        verify(mockJobServerClientOutput, times(1)).isClientConnected();
        verify(mockJobServerWorker, times(1)).doWork(any());
        verify(mockJobServerWorker, times(1)).getRescheduleInterval();
        verify(mockScheduledExecutorService, times(1)).schedule(jobServerRunnable, 5, TimeUnit.SECONDS);

        verifyNoMoreInteractions(mockJobServerClientOutput);
        verifyNoMoreInteractions(mockJobServerWorker);
        verifyNoMoreInteractions(mockScheduledExecutorService); // Rescheduled
    }

    @Test
    public void testRunWithErrors() throws Exception {
        int returnCode = 123;

        // Given
        when(mockJobServerClientOutput.getOs()).thenReturn(mockOutputStream);
        when(mockJobServerClientOutput.isClientConnected()).thenReturn(true);
        when(mockJobServerWorker.doWork(any())).thenThrow(new Exception("Something Bad"));
        when(mockJobServerWorker.getReturnCode()).thenReturn(returnCode);

        // Run
        jobServerRunnable.run();

        // Verify
        verify(mockJobServerClientOutput, times(1)).getOs();
        verify(mockJobServerClientOutput, times(1)).isClientConnected();
        verify(mockJobServerClientOutput, times(1)).sendFail(returnCode);
        verify(mockJobServerWorker, times(1)).doWork(any());
        verify(mockJobServerWorker, times(1)).getName();
        verify(mockJobServerWorker, times(1)).getReturnCode();

        verifyNoMoreInteractions(mockJobServerClientOutput);
        verifyNoMoreInteractions(mockJobServerWorker);
        verifyNoInteractions(mockScheduledExecutorService); // No rescheduling
    }

    @Test
    public void testNoClient() {
        // Given
        when(mockJobServerClientOutput.isClientConnected()).thenReturn(false);

        // Run
        jobServerRunnable.run();

        // Verify
        verify(mockJobServerClientOutput, times(1)).isClientConnected();
        verify(mockJobServerWorker, times(1)).getName();

        verifyNoMoreInteractions(mockJobServerClientOutput);
        verifyNoMoreInteractions(mockJobServerWorker); // Nothing happened
        verifyNoInteractions(mockScheduledExecutorService);
    }

}
