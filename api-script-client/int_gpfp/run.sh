#!/bin/bash
set -e; # Stop on error

# Declare run specific parameters
effectiveDate="2014-09-30"
readmeEndDate="2014"
isFirstTime=true
isWorkbenchDataFixesRequired=true
headless=true
extensionName="SNOMED CT International Edition"
productName="SNOMED CT Release"
buildName="Int GPFP Build"
packageName="GPFP Release Package"

# Call api_client
source ../api_client.sh