#!/bin/bash

# get the path of the bash script so it can be run from any directory.
# note: will not work if last compnent of path is a symlink, in which case, your must do everything manually
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

if [[ $1 == "compile" ]]
then
	# compile the programs in the src directory into the bin directory
	javac -d "$SCRIPT_DIR"/bin -cp "$SCRIPT_DIR"/bin "$SCRIPT_DIR"/src/*
elif [[ $1 == "clean" ]]
then
	# remove all class files in bin
	rm "$SCRIPT_DIR"/bin/*
elif [[ $1 == "client" ]]
then
	# run the client program
	java -cp "$SCRIPT_DIR"/bin Participant $2
elif [[ $1 == "server" ]]
then
	# run the server program
	java -cp "$SCRIPT_DIR"/bin Coordinator $2
else
	echo "Error: invalid arguments"
	echo "Valid arguments are: compile, clean, client, server"
fi
