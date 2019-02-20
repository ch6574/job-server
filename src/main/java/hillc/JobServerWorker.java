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

import org.slf4j.Logger;

interface JobServerWorker {

    /**
     * Identity method.
     *
     * @return the name for this instance
     */
    String getName();

    /**
     * Method that would contain one's business logic.
     *
     * @param clientLog a logger that writes back to the client that called us
     * @return true if all work has completed, else false (and we will be rescheduled again)
     * @throws Exception to abort the client processing
     */
    boolean doWork(Logger clientLog) throws Exception;

    /**
     * @return The return code to be passed back to the client
     */
    int getReturnCode();

    /**
     * @return The interval, in seconds, between each re-scheduled run
     */
    int getRescheduleInterval();

}

