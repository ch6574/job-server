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

    private int interval;
    private String filename;

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
                return true;
            }
        } catch (IOException | InvalidPathException e) {
            clientLog.error("Problem with file processing!", e);
            throw e;
        }

        return false; // By default we re-schedule ourselves
    }

    @Override
    public int getRescheduleInterval() {
        return interval;
    }
}
