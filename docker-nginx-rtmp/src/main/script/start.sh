#!/bin/bash

docker run -d -p 80:80 -p 1935:1935 --name=kognition-nginx kognition-nginx

