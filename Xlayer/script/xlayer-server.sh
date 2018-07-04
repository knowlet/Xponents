script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

CMD=$1
XLAYER_PORT=$2 

case $CMD in

  'start')
    echo JAVA_HOME =  $JAVA_HOME
    echo $*
    cd $basedir
    logfile=$basedir/log/stderr.log
    stdout=$basedir/log/xlayer.log
    XPONENTS_SOLR=${XPONENTS_SOLR:-$basedir/xponents-solr/solr7}

    nohup java -Dopensextant.solr=$XPONENTS_SOLR -Xmx2g -Xms2g \
        -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$basedir/etc:$basedir/etc/*:$basedir/lib/*" org.opensextant.xlayer.server.xgeo.XlayerServer $XLAYER_PORT  2>$logfile > $stdout &
  ;;


  'stop')
    RESTAPI=http://localhost:$XLAYER_PORT/xlayer/rest/control/stop
    # Using curl, POST a JSON object to the service.
    curl "$RESTAPI"
  ;;

esac
