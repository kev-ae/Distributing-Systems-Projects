Group members: Taylor Wetterhan, Kevin Nguyen, David Yu

Instructions (using script)

1. Change the execution permissions for the auto.sh script if this script is not executable  
   $ chmod +x auto.sh

2. Type the following commands into the shell to compile, run, clean the program

    - To compile the programs  
       $ ./auto.sh compile

    - To clean the class files  
       $ ./auto.sh clean

    - To run the bootstrap replace config. Ensure the file has all the necessary configurations. Ctrl-z and bg to run in background if necessary.  
       $ ./auto.sh boot config  
       Ex: ./auto.sh boot bnconfig.txt

    - To run the nameserver replace config. Ensure the file has all the necessary configurations.
      $ ./auto.sh name config  
       Ex: ./auto.sh name nsconfig.txt

Manual  
If the script does not work properly, you can compile and run the programs using the commands below

-   To compile  
    $ javac -d bin -cp bin src/\*

    -   To run
        $ java -cp bin NameServer bnconfig.txt
        $ java -cp bin BootStrap nsconfig.txt

Statement:
This project was done in its entirety by Taylor Wetterhan, Kevin Nguyen, David Yu. We hereby
state that we have not received unauthorized help of any form

Note: With the way we program our servers, for the enter command, the last contacted server is the predecessor and the second to last is the successor.
