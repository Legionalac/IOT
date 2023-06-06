# IOT
Add this to mosquitto.conf /etc/mosquitto
>listener 4000\
>allow_anonymous true

Mosquitto broker: mosquitto -v -p 4000 -c mosquitto.conf in /etc/mosquitto\
To compile : ./compile.sh\
To run Controller : ./Controller.sh\
To run Sensors and Actuators : ./Sensors_Actuators.sh