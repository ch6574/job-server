#!/usr/bin/env bash
################################################################################
# Copyright (c) 2018, Christopher Hill <ch6574@gmail.com>
# GNU General Public License v3.0+ (see https://www.gnu.org/licenses/gpl-3.0.txt)
# SPDX-License-Identifier: GPL-3.0-or-later
################################################################################
set -euo pipefail

#
# Simple script that can talk to the JobServer and understand data sent back to it
#
RS=$'\x1e'  # ASCII 30 "record separator" single char
returnCode=-1

exec 3<> /dev/tcp/localhost/12345                        # Open the socket to the JobServer
printf "%s" "$@" >&3                                     # Write all supplied arguments
while read -r line <&3; do                               # Now read in a loop until the socket closes
    case "$line" in
    ${RS}L*)                                             # logging, so print to stdout (minus first 2 chars)
        printf "%s\\n" "${line#??}"
        ;;
    ${RS}C*)                                             # control, so examine the command
        case "${line#??}" in
        DONE!*)
            returnCode="${line##*!}"                    # everything after the '!'
            ;;
        FAIL!*)
            printf "Something failed\\n"
            returnCode="${line##*!}"                    # everything after the '!'
            ;;
        *)
            printf "Unknown command '%s'\\n" "${line#??}"
            ;;
        esac
        ;;
    *)
        printf "Unknown response: '%s'\\n" "$line"
        ;;
    esac
done

exit "$returnCode"
