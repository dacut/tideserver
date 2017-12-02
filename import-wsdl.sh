#!/bin/sh
wsimport -p gov.noaa.nos.coops.opendap.webservices.activestations -Xnocompile -keep -d noaa-coops-opendap/src -extension 'https://tides.kanga.org/wsdl/noaa-active-stations-fixed.wsdl'
wsimport -p gov.noaa.nos.coops.opendap.webservices.predictions -Xnocompile -keep -d noaa-coops-opendap/src -extension 'https://opendap.co-ops.nos.noaa.gov/axis/webservices/predictions/wsdl/Predictions.wsdl'
wsimport -p gov.noaa.nos.coops.opendap.webservices.waterlevelrawsixmin -Xnocompile -keep -d noaa-coops-opendap/src -extension 'https://opendap.co-ops.nos.noaa.gov/axis/webservices/waterlevelrawsixmin/wsdl/WaterLevelRawSixMin.wsdl'
wsimport -p gov.noaa.nos.coops.opendap.webservices.waterlevelverifiedsixmin -Xnocompile -keep -d noaa-coops-opendap/src -extension 'https://opendap.co-ops.nos.noaa.gov/axis/webservices/waterlevelverifiedsixmin/wsdl/WaterLevelVerifiedSixMin.wsdl'
wsimport -p gov.noaa.nos.coops.opendap.webservices.highlowtidepred -Xnocompile -keep -d noaa-coops-opendap/src -extension 'https://opendap.co-ops.nos.noaa.gov/axis/webservices/highlowtidepred/wsdl/HighLowTidePred.wsdl'
