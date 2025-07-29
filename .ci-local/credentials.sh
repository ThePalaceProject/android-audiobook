#!/bin/bash

#------------------------------------------------------------------------
# Utility methods
#

fatal()
{
  echo "credentials.sh: fatal: $1" 1>&2
  exit 1
}

info()
{
  echo "credentials.sh: info: $1" 1>&2
}

if [ -z "${CI_AWS_ACCESS_ID}" ]
then
  fatal "CI_AWS_ACCESS_ID is not defined"
fi
if [ -z "${CI_AWS_SECRET_KEY}" ]
then
  fatal "CI_AWS_SECRET_KEY is not defined"
fi
if [ -z "${MAVEN_CENTRAL_USERNAME}" ]
then
  fatal "MAVEN_CENTRAL_USERNAME is not defined"
fi
if [ -z "${MAVEN_CENTRAL_PASSWORD}" ]
then
  fatal "MAVEN_CENTRAL_PASSWORD is not defined"
fi

#------------------------------------------------------------------------
# Add the Gradle properties to the project properties.
#

mkdir -p "${HOME}/.gradle" ||
  fatal "could not create ${HOME}/.gradle"

CREDENTIALS_PATH=$(realpath ".ci/credentials") ||
  fatal "could not resolve credentials path"

ASSETS_PATH="${CREDENTIALS_PATH}/Certificates/Palace/Android"

if [ ! -d "${ASSETS_PATH}" ]
then
  fatal "${ASSETS_PATH} does not exist, or is not a directory"
fi

cat >> "${HOME}/.gradle/gradle.properties" <<EOF
org.thepalaceproject.lcp.enabled=true
org.thepalaceproject.lcp.profile=prod
org.thepalaceproject.findaway.enabled=true
org.thepalaceproject.overdrive.enabled=true

org.thepalaceproject.s3.depend=true
org.thepalaceproject.aws.access_key_id=${CI_AWS_ACCESS_ID}
org.thepalaceproject.aws.secret_access_key=${CI_AWS_SECRET_KEY}
org.thepalaceproject.app.credentials.palace=${CREDENTIALS_PATH}

mavenCentralUsername=${MAVEN_CENTRAL_USERNAME}
mavenCentralPassword=${MAVEN_CENTRAL_PASSWORD}
EOF
