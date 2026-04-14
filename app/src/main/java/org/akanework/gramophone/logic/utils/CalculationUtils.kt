/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [CalculationUtils] contains some methods for internal
 * calculation.
 */
object CalculationUtils {

    /**
     * [convertDurationToTimeStamp] makes a string format
     * of duration (presumably long) converts into timestamp
     * like 300 to 5:00.
     *
     * @param duration
     * @return
     */
    fun convertDurationToTimeStamp(duration: Long): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 - minutes * 60
        if (seconds < 10) {
            return "$minutes:0$seconds"
        }
        return "$minutes:$seconds"
    }

    /**
     * convertUnixTimestampToMonthDay:
     *   Converts unix timestamp to Month - Day format.
     */
    fun convertUnixTimestampToMonthDay(unixTimestamp: Long): String =
        SimpleDateFormat(
            "MM-dd",
            Locale.getDefault()
        ).format(
            Date(unixTimestamp * 1000)
        )

    fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

}
