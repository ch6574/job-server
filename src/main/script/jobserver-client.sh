#!/bin/bash -e

#
# Simple script that can talk to the JobServer and understand data sent back to it
#
RS=$'\x1e'  # ASCII "record separator"
done=false

exec 3<> /dev/tcp/localhost/12345                        # Open the socket to the JobServer
printf "%s" "$@" >&3                                     # Write all supplied arguments
while read -r line <&3; do                               # Now read in a loop until the socket closes
    case "$line" in
    ${RS}L*)                                             # logging, so print to stdout
        printf "%s\n" "${line#??}"
        ;;
    ${RS}C*)                                             # control, so examine the command
        case "${line#??}" in
        DONE!)
            done=true
            ;;
        FAIL!)
            printf "Something failed\n"
            ;;
        *)
            printf "Unknown command '%s'\n" "${line#??}"
            ;;
        esac
        ;;
    *)
        printf "Unknown response: '%s'\n" "$line"
        ;;
    esac
done

# Determine return code
if [[ "$done" = true ]]; then
    exit 0
else
    exit 1
fi