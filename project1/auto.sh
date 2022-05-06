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
elif [[ $1 == "run" ]]
then
	if [[ $2 == "client" ]]
	then
		# run the client program
		java -cp "$SCRIPT_DIR"/bin myftp $3 $4
	elif [[ $2 == "server" ]]
	then
		# run the server program
		java -cp "$SCRIPT_DIR"/bin myftpserver $3
	else
		echo "Error: cannot find program"
		echo "Valid programs is only client and server"
		echo "Check for typo in 'run client' or 'run server'"
	fi
else
	echo "Error: invalid arguments"
	echo "Valid arguments are: compile, clean, run client, run server"
fi
