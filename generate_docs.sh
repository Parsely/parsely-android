#!/bin/bash

doxygen ParselyExample/ParselyAndroid.doxyfile
rsync -Pavz docs/html/* parsely.com:/data/vhosts/www.parsely.com/sdk/android
