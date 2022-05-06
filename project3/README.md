Group members: Taylor Wetterhan, Kevin Nguyen, David Yu  

Instructions (using script)  
1. Change the execution permissions for the auto.sh script if this script is not executable  
    $ chmod +x auto.sh

2. Type the following commands into the shell to compile, run, clean the program  
    - To compile the programs  
        $ ./auto.sh compile

    - To clean the class files  
        $ ./auto.sh clean

    - To run the Coordinator program (server) replace config.file. Ensure the file has all the necessary configurations. Ctrl-z and bg to run in background if necessary.  
        $ ./auto.sh server config.file 
        Ex: ./auto.sh server PP3-coordinator-conf.txt

    - To run the Participant program (client) replace config.file. Ensure the file has all the necessary configurations.
        $ ./auto.sh client config.file  
        Ex: ./auto.sh client PP3-participant-conf.txt

Manual  
If the script does not work properly, you can compile and run the programs using the commands below  
    - To compile  
        $ javac -d bin -cp bin src/*

    - To run, replace normalPort, terminalPort, and serverName with the approprate values  
        $ java -cp bin Coordinator PP3-coordinator-conf.txt  
        $ java -cp bin Participant PP3-participant-conf.txt  

If you are running these programs on the same computer: after running the server, you should open up a new shell and run the client.  
If on different computers, I recommend having 2 copies of this project directory, one on the server and one on another computer. Then run Coordinator on the server and then run Participant on the other computer using the commands above.  

Professor Ramaswamy told us that it is okay for the server to not shut down cleanly so if you want to shut down the Coordinator, you have to press ctrl-c. The Participant can close with the "quit" command once disconnected and deregistered. 

Statement:
This project was done in its entirety by Taylor Wetterhan, Kevin Nguyen, David Yu. We hereby
state that we have not received unauthorized help of any form