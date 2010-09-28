#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License. 

# Loading functions
. ./functions.sh

# Reading variables file and asking questions
lines=`wc -l < ./variables.sh`
count=1
lines=`expr ${lines:-0} + 1`
while [ $count -lt $lines ]
do
    ask_param $count
    count=`expr ${count:-0} + 1`
done

#
# Starting installation
#

# Verifying the user is root
#if ( test `id -un` != "root" )
#then
#    echo "Only root can install this software."
#    echo "Apache DS installation has failed."
#    exit 1 ;
#fi

# Installing
echo "Installing..."

# Filtering apacheds script file
sed -e "s;@installation.directory@;${APACHEDS_HOME_DIRECTORY};" ../server/bin/apacheds > ../server/bin/apacheds.tmp
verifyExitCode
mv ../server/bin/apacheds.tmp ../server/bin/apacheds
verifyExitCode
sed -e "s;@instances.directory@;${INSTANCES_HOME_DIRECTORY};" ../server/bin/apacheds > ../server/bin/apacheds.tmp
verifyExitCode
mv ../server/bin/apacheds.tmp ../server/bin/apacheds
verifyExitCode
sed -e "s;@user@;${RUN_AS_USER};" ../server/bin/apacheds > ../server/bin/apacheds.tmp
verifyExitCode
mv ../server/bin/apacheds.tmp ../server/bin/apacheds
verifyExitCode

# Copying the server files
mkdir -p $APACHEDS_HOME_DIRECTORY
verifyExitCode
cp -r ../server/* $APACHEDS_HOME_DIRECTORY
verifyExitCode

# Creating instances home directory
mkdir -p $INSTANCES_HOME_DIRECTORY
verifyExitCode

# Creating the default instance home directory
DEFAULT_INSTANCE_HOME_DIRECTORY=$INSTANCES_HOME_DIRECTORY/$DEFAULT_INSTANCE_NAME
verifyExitCode
mkdir -p $DEFAULT_INSTANCE_HOME_DIRECTORY
verifyExitCode
mkdir -p $DEFAULT_INSTANCE_HOME_DIRECTORY/conf
verifyExitCode
mkdir -p $DEFAULT_INSTANCE_HOME_DIRECTORY/log
verifyExitCode
mkdir -p $DEFAULT_INSTANCE_HOME_DIRECTORY/partitions
verifyExitCode
mkdir -p $DEFAULT_INSTANCE_HOME_DIRECTORY/run
verifyExitCode

# Filtering default instance wrapper.conf file
sed -e "s;@installation.directory@;${APACHEDS_HOME_DIRECTORY};" ../instance/wrapper.conf > ../instance/wrapper.conf.tmp
verifyExitCode
mv ../instance/wrapper.conf.tmp ../instance/wrapper.conf
verifyExitCode

# Copying the default instance files
cp ../instance/wrapper.conf $DEFAULT_INSTANCE_HOME_DIRECTORY/conf/
verifyExitCode
cp ../instance/log4j.properties $DEFAULT_INSTANCE_HOME_DIRECTORY/conf/
verifyExitCode

# Filtering and copying the init.d script
sed -e "s;@installation.directory@;${APACHEDS_HOME_DIRECTORY};" ../instance/apacheds-init > ../instance/apacheds-init.tmp
verifyExitCode
mv ../instance/apacheds-init.tmp ../instance/apacheds-init
verifyExitCode
sed -e "s;@default.instance.name@;$DEFAULT_INSTANCE_NAME;" ../instance/apacheds-init > ../instance/apacheds-init.tmp
verifyExitCode
mv ../instance/apacheds-init.tmp ../instance/apacheds-init
verifyExitCode
cp ../instance/apacheds-init $STARTUP_SCRIPT_DIRECTORY/apacheds-$APACHEDS_VERSION-$DEFAULT_INSTANCE_NAME
verifyExitCode

# Setting the correct permissions on executable files
chmod +x $STARTUP_SCRIPT_DIRECTORY/apacheds-$APACHEDS_VERSION-$DEFAULT_INSTANCE_NAME
verifyExitCode
chmod +x $APACHEDS_HOME_DIRECTORY/bin/apacheds
verifyExitCode
chmod +x $APACHEDS_HOME_DIRECTORY/bin/wrapper
verifyExitCode

# Creating the apacheds user (only if needed)
USER=`eval "id -u -n $RUN_AS_USER"`
if [ ! "X$RUN_AS_USER" = "X$USER" ]
then
	/usr/sbin/groupadd $RUN_AS_USER >/dev/null 2>&1 || :
	verifyExitCode
	/usr/sbin/useradd -g $RUN_AS_USER -d $APACHEDS_HOME_DIRECTORY $RUN_AS_USER >/dev/null 2>&1 || :
	verifyExitCode
fi

# Modifying owner
chown -R $RUN_AS_USER:$RUN_AS_USER $APACHEDS_HOME_DIRECTORY
chown -R $RUN_AS_USER:$RUN_AS_USER $INSTANCES_HOME_DIRECTORY
chown $RUN_AS_USER:$RUN_AS_USER $STARTUP_SCRIPT_DIRECTORY/apacheds-$APACHEDS_VERSION-$DEFAULT_INSTANCE_NAME
