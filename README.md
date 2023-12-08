# Peer-to-Peer Network
This is an implementation of a [pastry](https://en.wikipedia.org/wiki/Pastry_(DHT)) peer-to-peer network in Java. It is based off an assignment offered through my local university.

## Node types

It is constituted with three types of nodes -- the *Discovery*, *Peer*, and *StoreData*.

The *Discovery* keeps track of *live* peers in the network.

The *Peer* maintains an up-to-date routing table and leafset for efficiently relaying messages between peers in the system. It also stores and serves files.

The *StoreData* node stores to and retrieves files from the network.

## How to use it
I've used *SDKMAN!* to install packages like *gradle* and *java*. *sdk current* reports that I'm using *gradle 8.1.1* and *java 17.0.8.1-tem*. I haven't compiled the project using any other versions, so if you're not using these, you'll just have to test for yourself.

The *Discovery* creates a ServerSocket that must be accessible by all *Peers* and the *StoreData* node. The hostname and port for this ServerSocket is configurable in the *application.properties* file in the *config* folder via the *discoveryHost* and *discoveryPort* properties. *discoveryPort* is actually used to set up the ServerSocket, while *discoveryHost* is implicit -- this is the hostname of the machine you plan to run the *Discovery*. *logLevel* controls the level of logging printed to the console. Use *info* for fewer messages. 

The scripts *osx.sh* and *ubuntu.sh* each do the same thing, but the former is intended for macOS and the latter for Ubuntu. Each script compiles the project with gradle, and starts the *Discovery* node in the currently-open terminal window. Two new terminal windows are then spawned. Executing the command *./osx.sh s* (or *./ubuntu.sh s*) in one of the new windows will start the *StoreData*. Executing *./osx.sh* (or *./ubuntu.sh*) in the other will start nine *Peers* in nine different terminal tabs. The number of *Peers* to launch can be configured in the scripts.

Commands can then be issued to each of the running nodes. The command *help* works at each node, and prints a list of valid commands.

To use Docker instead of the scripts, change *discoveryHost* in the *application.properties* file to *discovery*, navigate to the *docker* folder in the project directory using your terminal, and, assuming Docker is already installed and running, use command *docker compose build* to first build the project, then *docker compose up -d* to run the project in detached mode. Then, to attach to any of the running containers, use *docker container attach <container_name>*. Detach from the the container using *CTRL-p-CTRL-q* (no dashes), which is the [escape sequence](https://docs.docker.com/engine/reference/commandline/attach/). To shut down the session, use *docker compose down*.

*Note*: All files read from and written to disk are done so in one command using the *Files* package, with either *Files.readAllBytes()* or *Files.write(byte[])*. This is easy and convenient, but each requires that the entire file be kept in memory for part of the operation. So, with very large files, the JVM will run out of memory if not configured with this in mind.
