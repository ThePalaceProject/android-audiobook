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
# Add the various configuration properties.
#

mkdir -p "${HOME}/.gradle" ||
  fatal "could not create ${HOME}/.gradle"

cat >> "${HOME}/.gradle/gradle.properties" <<EOF
org.thepalaceproject.lcp.enabled=true
org.thepalaceproject.lcp.profile=prod
org.thepalaceproject.findaway.enabled=true
org.thepalaceproject.overdrive.enabled=true
EOF
