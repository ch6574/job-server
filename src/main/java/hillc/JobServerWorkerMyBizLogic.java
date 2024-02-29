/*******************************************************************************
 * Copyright (c) 2018, Christopher Hill <ch6574@gmail.com>
 * GNU General Public License v3.0+ (see https://www.gnu.org/licenses/gpl-3.0.txt)
 * SPDX-License-Identifier: GPL-3.0-or-later
 ******************************************************************************/
package hillc;

import net.jcip.annotations.NotThreadSafe;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Demo business logic
 */
@NotThreadSafe
class JobServerWorkerMyBizLogic implements JobServerWorker {

    private final int interval;
    private final String filename;
    private int returnCode = 1; // default return code

    /**
     * Constructor
     *
     * @param interval The interval, in seconds, between each re-scheduled run
     * @param filename The filename provided by the client
     */
    JobServerWorkerMyBizLogic(final int interval, final String filename) {
        this.interval = interval;
        this.filename = Objects.requireNonNull(filename);
    }

    @Override
    public String getName() {
        return filename;
    }

    @Override
    public boolean doWork(final Logger clientLog) throws Exception {
        try {
            //
            // The business logic, e.g. checking files...
            //

            // Check if the file exists
            clientLog.info("Checking for {}", filename);
            Path path = Paths.get(filename);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                // Delete it - this is our "work" we have been waiting to do
                clientLog.info("Found file '{}', deleting it and sending reply to calling client", filename);
                Files.delete(path);

                returnCode = 0;
                return true;
            }
        } catch (IOException | InvalidPathException e) {
            clientLog.error("Problem with file processing!", e);
            throw e;
        }

        return false; // By default, we re-schedule ourselves
    }

    @Override
    public int getReturnCode() {
        return returnCode;
    }

    @Override
    public int getRescheduleInterval() {
        return interval;
    }
}
