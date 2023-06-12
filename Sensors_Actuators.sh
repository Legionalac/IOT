#!/bin/sh
gnome-terminal --tab --title="Temp" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Sensor 192.168.5.10 Sensor Temperature 0 
gnome-terminal --tab --title="Temp" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Sensor 10 Sensor Temperature 1
gnome-terminal --tab --title="Humidity" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Sensor 20 Sensor Humidity 2
gnome-terminal --tab --title="Fan" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Actuator 192.168.5.20 Actuator Fan 
gnome-terminal --tab --title="Pump" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Actuator 192.168.5.20 Actuator Pump 
gnome-terminal --tab --title="Application" -- java -cp org.eclipse.paho.client.mqttv3-1.2.5.jar: Application 192.168.5.20 Application 
