#!/bin/sh
gnome-terminal --tab --title="Temp" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Sensor 192.168.5.10 Sensor Temperature 
gnome-terminal --tab --title="Fan" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Sensor 192.168.5.20 Actuator Fan 
