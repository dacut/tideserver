
package gov.noaa.nos.coops.opendap.webservices.predictions;

import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.2.9-b130926.1035
 * Generated source version: 2.2
 * 
 */
@WebServiceClient(name = "PredictionsService", targetNamespace = "https://opendap.co-ops.nos.noaa.gov/axis/webservices/predictions/wsdl", wsdlLocation = "https://opendap.co-ops.nos.noaa.gov/axis/webservices/predictions/wsdl/Predictions.wsdl")
public class PredictionsService
    extends Service
{

    private final static URL PREDICTIONSSERVICE_WSDL_LOCATION;
    private final static WebServiceException PREDICTIONSSERVICE_EXCEPTION;
    private final static QName PREDICTIONSSERVICE_QNAME = new QName("https://opendap.co-ops.nos.noaa.gov/axis/webservices/predictions/wsdl", "PredictionsService");

    static {
        URL url = null;
        WebServiceException e = null;
        try {
            url = new URL("https://opendap.co-ops.nos.noaa.gov/axis/webservices/predictions/wsdl/Predictions.wsdl");
        } catch (MalformedURLException ex) {
            e = new WebServiceException(ex);
        }
        PREDICTIONSSERVICE_WSDL_LOCATION = url;
        PREDICTIONSSERVICE_EXCEPTION = e;
    }

    public PredictionsService() {
        super(__getWsdlLocation(), PREDICTIONSSERVICE_QNAME);
    }

    public PredictionsService(WebServiceFeature... features) {
        super(__getWsdlLocation(), PREDICTIONSSERVICE_QNAME, features);
    }

    public PredictionsService(URL wsdlLocation) {
        super(wsdlLocation, PREDICTIONSSERVICE_QNAME);
    }

    public PredictionsService(URL wsdlLocation, WebServiceFeature... features) {
        super(wsdlLocation, PREDICTIONSSERVICE_QNAME, features);
    }

    public PredictionsService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public PredictionsService(URL wsdlLocation, QName serviceName, WebServiceFeature... features) {
        super(wsdlLocation, serviceName, features);
    }

    /**
     * 
     * @return
     *     returns PredictionsPortType
     */
    @WebEndpoint(name = "Predictions")
    public PredictionsPortType getPredictions() {
        return super.getPort(new QName("https://opendap.co-ops.nos.noaa.gov/axis/webservices/predictions/wsdl", "Predictions"), PredictionsPortType.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns PredictionsPortType
     */
    @WebEndpoint(name = "Predictions")
    public PredictionsPortType getPredictions(WebServiceFeature... features) {
        return super.getPort(new QName("https://opendap.co-ops.nos.noaa.gov/axis/webservices/predictions/wsdl", "Predictions"), PredictionsPortType.class, features);
    }

    private static URL __getWsdlLocation() {
        if (PREDICTIONSSERVICE_EXCEPTION!= null) {
            throw PREDICTIONSSERVICE_EXCEPTION;
        }
        return PREDICTIONSSERVICE_WSDL_LOCATION;
    }

}