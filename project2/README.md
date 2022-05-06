Group members: Taylor Wetterhan, Kevin Nguyen, David Yu  

Instructions (using script)  
1. Change the execution permissions for the auto.sh script if this script is not executable  
    $ chmod +x auto.sh

2. Type the following commands into the shell to compile, run, clean the program  
    - To compile the programs  
        $ ./auto.sh compile

    - To clean the class files  
        $ ./auto.sh clean

    - To run the myftpserver program (server) replace normalPort and terminalPort. Ctrl-z and bg to run in background  
        $ ./auto.sh run server normalPort terminalPort  
        Ex: ./auto.sh run server 5000 6000  

    - To run the myftp program (client) replace serverName with the server that myftpserver is running on and normalPort/terminalPort with the port the server is listening on  
        $ ./auto.sh run client serverName normalPort terminalPort  
        Ex: ./auto.sh run client vcf0.cs.uga.edu 5000 6000

Manual  
If the script does not work properly, you can compile and run the programs using the commands below  
    - To compile  
        $ javac -d bin -cp bin src/*

    - To run, replace normalPort, terminalPort, and serverName with the approprate values  
        $ java -cp bin myftpserver normalPort terminalPort  
        $ java -cp bin myftp serverName normalPort terminalPort  

If you are running these program on the same computer: after running the server, you should open up a new shell and run the client.  
If on different computers, I recommend having 2 copies of this project directory, one on the server and one on another computer. Then run myftpserver on the server and then run myftp on the other computer using the commands above.  

Professor Ramaswamy told us that it is okay for the server to not shut down cleanly so if you want to shut down the myftpserver, you have to press ctrl-c  

Statement:
This project was done in its entirety by Taylor Wetterhan, Kevin Nguyen, David Yu. We hereby
state that we have not received unauthorized help of any form