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

org.thepalaceproject.app.credentials.palace=${CREDENTIALS_PATH}
EOF
