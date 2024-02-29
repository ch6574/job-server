/*******************************************************************************
 * Copyright (c) 2018, Christopher Hill <ch6574@gmail.com>
 * GNU General Public License v3.0+ (see https://www.gnu.org/licenses/gpl-3.0.txt)
 * SPDX-License-Identifier: GPL-3.0-or-later
 ******************************************************************************/
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

