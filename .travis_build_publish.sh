#!/bin/bash

if [ ${TRAVIS_PULL_REQUEST} = 'false' ] && [ ${TRAVIS_BRANCH} = 'master' ]; then
  ./gradlew -PsonatypeUsername="${SONATYPE_USERNAME}" -PsonatypePassword="${SONATYPE_PASSWORD}" build test uploadArchives
else
  ./gradlew build test
fi
