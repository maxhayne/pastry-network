#!/bin/bash
set -e # Exit script if any command fails
#
# Assuming tmux is installed, this script will create a new session
# called 'pastry', and fill the session with a Discovery, Peers,
# and a StoreData as windows.
#
# Modify the 'MULTI' variable to contain as many peers as you'd like.
#
# This assumes that you have some knowledge of tmux.


MULTI="1 2 3 4 5 6 7 8 9"

DIR="$( cd "$( dirname "$0" )" && pwd )"
JAR_PATH="$DIR/conf/:$DIR/build/libs/pastry-network.jar"

START_DISCOVERY="java -cp $JAR_PATH cs555.system.node.Discovery"
START_PEER="java -cp $JAR_PATH cs555.system.node.Peer"
START_STOREDATA="java -cp $JAR_PATH cs555.system.node.StoreData"

# Print lines and build project
LINES=`find . -name "*.java" -print | xargs wc -l | grep "total" | awk '{$1=$1};1'`
echo Project has "$LINES" lines
gradle clean
gradle build

# Create pastry session and all necessary windows
tmux new-session -d -s pastry
tmux rename-window -t pastry:0 "Discovery"
for server in $MULTI
do
	tmux new-window -d -n Peer$server
done
tmux new-window -d -n StoreData

# Send start commands to all windows and execute
echo ""
echo "Attempting to start Discovery..."
tmux send-keys -t pastry:Discovery "$START_DISCOVERY" C-m
sleep 3
echo "Attempting to start Peers..."
for server in $MULTI
do
	tmux send-keys -t pastry:Peer$server "$START_PEER" C-m
	sleep 0.2
done
echo "Attempting to start StoreData..."
tmux send-keys -t pastry:StoreData "$START_STOREDATA" C-m

# Minimal instructions
echo "tmux session 'pastry' has been created with Discovery, Peers, and StoreData windows"
echo ""
echo "useful tips:"
echo "  - list windows: tmux lsw"
echo "  - attach session: tmux attach -t pastry"
echo "  - kill session: tmux kill-session -t pastry"
echo "  - use 'Ctrl-B n' or 'Ctrl-B p' to go to next, or previous window"
echo "  - use 'Ctrl-B d' to detach from session and return to shell"
echo ""
