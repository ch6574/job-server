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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

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
        verifyZeroInteractions(mockScheduledExecutorService); // No rescheduling
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
        verifyZeroInteractions(mockScheduledExecutorService); // No rescheduling
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
        verifyZeroInteractions(mockScheduledExecutorService);
    }

}
