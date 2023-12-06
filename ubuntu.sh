#!/bin/bash
#
# run script for Ubuntu.  Run in the top level directory of project.
# Controller will start in the terminal, and a new window will be opened.
# This new window will spawn MULTI Chunk Servers.
#

# Originally written by Jason Stock, Graduate Student at CSU in Computer Science
# I've taken the base file and modified it so that it works with my code

MULTI="1 2 3 4 5 6 7 8 9"

DIR="$( cd "$( dirname "$0" )" && pwd )"
JAR_PATH="$DIR/conf/:$DIR/build/libs/pastry-network.jar"
COMPILE="$( ps -ef | grep [c]s555.system.node.Discovery )"

SCRIPT="java -cp $JAR_PATH cs555.system.node.Peer"

new_tab ()  {
	gnome-terminal --tab -- bash -c "$SCRIPT $1"
}

if [[ -z "$COMPILE" ]]
then
LINES=`find . -name "*.java" -print | xargs wc -l | grep "total" | awk '{$1=$1};1'`
    echo Project has "$LINES" lines
    gradle clean
    gradle build
    gnome-terminal
    gnome-terminal
    java -cp $JAR_PATH cs555.system.node.Discovery;
elif [[ -n "$1" ]] && [[ $1 == "s" ]]
then
    java -cp $JAR_PATH cs555.system.node.StoreData;
else
    if [[ -n "$MULTI" ]]
    then
        for tab in `echo $MULTI`
        do
            new_tab
        done
    fi
fi
