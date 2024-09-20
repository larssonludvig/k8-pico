#!/bin/bash

for f in $(find . -name "Dockerfile.*" -type f) 
do
	tag="${f##*.}:latest"
	docker build -f $f -t $tag .
done
