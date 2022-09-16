#!/bin/bash -e

native-image -jar "$1" \
--no-fallback \
-H:Name=output
