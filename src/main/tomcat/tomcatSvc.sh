#!/bin/bash


#############################################################
# look at gist.github.com/peterc/404672
# to cleanly put this in /etc/init.d
#############################################################

printf "\n"


#############################################################
# usage statement
#############################################################
USAGE='usage: tomcatSvc start-debug|start|stop|restart-debug|restart'

ARGS=( "$@" )
TOMCAT_CMD=$1


#############################################################
# check if the user passed in a valid command
#############################################################
if [[ "$TOMCAT_CMD" != start && "$TOMCAT_CMD" != stop && "$TOMCAT_CMD" != restart
		&& "$TOMCAT_CMD" != restart-debug && "$TOMCAT_CMD" != start-debug && "$TOMCAT_CMD" != stop-now ]]; then
    printf "$USAGE\n\n"
    exit 1
fi

#############################################################
## SETUP CATALINA_HOME
#############################################################
printf "*** Exporting CATALINA_HOME\n"
export CATALINA_HOME=$(pwd)
printf "************** CATALINA_HOME: $CATALINA_HOME\n"
printf "\n"


#############################################################
# get the current directory and eventually set YOTTAM_LOGS
#############################################################
CURR_DIR="${BASH_SOURCE[0]}"
while [ -h "$CURR_DIR" ]; do # resolve $CURR_DIR until the file is no longer a symlink
    TARGET="$(readlink "$CURR_DIR")"
    if [[ $CURR_DIR == /* ]]; then
        printf "Current dir '$CURR_DIR' is an absolute symlink to '$TARGET'\n"
        CURR_DIR="$TARGET"
    else
        BADDATA_HOME="$( dirname "$CURR_DIR" )"
        printf "Current dir '$CURR_DIR' is a relative symlink to '$TARGET' (relative to '$BADDATA_HOME')\n"
        CURR_DIR="$BADDATA_HOME/$TARGET" # if $CURR_DIR was a relative symlink, we need to resolve it realtive to the path where the symlink file was located
    fi
done
printf "Current Dir: '$CURR_DIR'\n"
RDIR="$( dirname "$CURR_DIR" )"


#############################################################
# set BADDATA_HOME to use later for BADDATA_LOGS
#############################################################
BADDATA_HOME="$( cd -P "$( dirname "$CURR_DIR" )" && pwd )"

printf "BADDATA_HOME: '$BADDATA_HOME'\n"


#############################################################
# export the BADDATA_LOGS environment variable
#############################################################
export BADDATA_LOGS=$BADDATA_HOME/logs

#############################################################
# export the JPDA address and transport socket for remote debugging
#############################################################
export JPDA_ADDRESS=8000
export JPDA_TRANSPORT=dt_socket

#############################################################
# run the requested command
# use "run" to run in the foreground
# use "start" to run in the background
#############################################################
case $TOMCAT_CMD in
    start)
        echo "executing start"
        ./bin/startup.sh start
        ;;
    start-debug)
        echo "executing start"
        ./bin/catalina.sh jpda run
        ;;
    stop)
        echo "executing stop"
        ./bin/shutdown.sh
        ;;
    stop-now)
    	echo "forcing tomcat to stop"
    	./bin/shutdown.sh -force
    	;;
    restart)
        echo "executing restart"
        ./bin/shutdown.sh
        ./bin/startup.sh start
        ;;
    restart-debug)
        echo "executing restart"
        ./bin/shutdown.sh
        ./bin/catalina.sh jpda run
        ;;
esac

printf "\n"

