#!/bin/sh

cp $HOME/.viollier/viollier-cacerts.jks ./target/viollier-cacerts.jks

docker build -t docker-dev.artifactory.viollier.ch/hapi-fhir-jpaserver-starter:$1 .

