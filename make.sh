#!/bin/bash -ex
AWS_SDK_VERSION="1.11.241"
BUILD_DIR=build/make-sh
CLASS_OUT_DIR=$BUILD_DIR/classes
TMPDIR=$BUILD_DIR/tmp
OUTDIR=$BUILD_DIR/out
JARFILE=tideserver-lambda.jar
JAVAC_FLAGS="-g -d $CLASS_OUT_DIR -parameters -source 1.8 -target 1.8"
KOTLINC_FLAGS="-d $CLASS_OUT_DIR"
LIBRARY_JARS="lib/aws-java-sdk-$AWS_SDK_VERSION.jar:lib/aws-java-sdk-cloudwatch-$AWS_SDK_VERSION.jar:lib/aws-java-sdk-core-$AWS_SDK_VERSION.jar:lib/aws-java-sdk-s3-$AWS_SDK_VERSION.jar:lib/commons-codec-1.9.jar:lib/commons-logging-1.1.3.jar:lib/httpclient-4.5.2.jar:lib/httpcore-4.4.4.jar:lib/ion-java-1.0.2.jar:lib/jackson-annotations-2.6.0.jar:lib/jackson-core-2.6.7.jar:lib/jackson-databind-2.6.7.1.jar:lib/jackson-dataformat-cbor-2.6.7.jar:lib/javax.json-1.1.jar:lib/javax.json-api-1.1.jar:lib/jmespath-java-1.11.241.jar:lib/joda-time-2.8.1.jar:lib/joda-time-2.9.9.jar:lib/log4j-1.2.17.jar:lib/log4j-api-2.8.2.jar:lib/log4j-core-2.8.2.jar:lib/netty-buffer-4.1.17.Final.jar:lib/netty-codec-4.1.17.Final.jar:lib/netty-codec-http-4.1.17.Final.jar:lib/netty-common-4.1.17.Final.jar:lib/netty-handler-4.1.17.Final.jar:lib/netty-resolver-4.1.17.Final.jar:lib/netty-transport-4.1.17.Final.jar"
LAMBDA_RUNTIME="lib/aws-lambda-java-core-1.1.0.jar:lib/aws-lambda-java-log4j-1.0.0.jar:lib/aws-lambda-java-log4j2-1.0.0.jar"
CLASSPATH="$CLASS_OUT_DIR:$LIBRARY_JARS:$LAMBDA_RUNTIME"

! test -e $BUILD_DIR || rm -rf $BUILD_DIR
mkdir -p $CLASS_OUT_DIR
mkdir -p $TMPDIR
mkdir -p $OUTDIR

NOAA_SRCDIR=noaa-coops-opendap/src
TIDESERVER_SRCDIR=tideserver-lambda/src

javac $JAVAC_FLAGS -classpath $CLASSPATH $(find $NOAA_SRCDIR -name \*.java)
kotlinc-jvm $KOTLINC_FLAGS -classpath $CLASSPATH  $(find $TIDESERVER_SRCDIR -name \*.kt)

cp -r $TIDESERVER_SRCDIR/META-INF $CLASS_OUT_DIR

for libjar in $(echo $LIBRARY_JARS | sed -e 's/:/ /g'); do
  unzip -n $libjar -d $CLASS_OUT_DIR
done

jar cvfm $OUTDIR/$JARFILE $CLASS_OUT_DIR/META-INF/MANIFEST.MF -C $CLASS_OUT_DIR .
