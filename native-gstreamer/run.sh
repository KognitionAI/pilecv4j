#!/bin/sh -x

gst-launch-1.0 --gst-plugin-path=./build uridecodebin uri="file:///home/jim/Videos/Dave Smith Libertas (2017).mp4" ! videoconvert ! breakout ! videoconvert ! xvimagesink

