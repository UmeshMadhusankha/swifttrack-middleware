package com.swiftlogistics.cmsadapter.cms;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.ClientHttpRequestMessageSender;

/** Builds the SOAP client from the classes generated out of cms.wsdl. */
@Configuration
public class CmsSoapConfig {

    /** Package the jaxb2-maven-plugin writes the generated classes into. */
    private static final String GENERATED_PACKAGE = "com.swiftlogistics.cmsadapter.generated";

    /**
     * Converts the generated Java objects to and from XML.
     *
     * Handing it the package name is enough; it finds the generated classes and
     * learns their XML shape from the annotations the generator added.
     */
    @Bean
    public Jaxb2Marshaller cmsMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(GENERATED_PACKAGE);
        return marshaller;
    }

    /**
     * The SOAP client itself.
     *
     * The default transport has no timeouts at all, so a CMS that accepts the
     * connection and then goes quiet would block this adapter's thread forever.
     * Swapping in a request factory with both timeouts set is what turns that
     * hang into an exception we can report as a failed step.
     */
    @Bean
    public WebServiceTemplate cmsWebServiceTemplate(
            Jaxb2Marshaller cmsMarshaller,
            @Value("${cms.endpoint}") String endpoint,
            @Value("${cms.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${cms.read-timeout-ms}") long readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        WebServiceTemplate template = new WebServiceTemplate(cmsMarshaller);
        template.setDefaultUri(endpoint);
        template.setMessageSender(new ClientHttpRequestMessageSender(requestFactory));
        return template;
    }
}
