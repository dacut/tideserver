#!/bin/bash -ex
docker build --tag tideserver:latest --rm .
mkdir -p export
rm -f export/lambda.zip
docker run --volume ${PWD}/export:/export tideserver:latest \
  cp /lambda.zip /export/lambda.zip
