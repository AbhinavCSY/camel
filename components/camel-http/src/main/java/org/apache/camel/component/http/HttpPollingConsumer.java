/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.http;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.helper.LoadingByteArrayOutputStream;
import org.apache.camel.impl.PollingConsumerSupport;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.params.HttpConnectionParams;

/**
 * A polling HTTP consumer which by default performs a GET
 *
 * @version $Revision$
 */
public class HttpPollingConsumer extends PollingConsumerSupport {
    private final HttpEndpoint endpoint;
    private HttpClient httpClient;

    public HttpPollingConsumer(HttpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        httpClient = endpoint.createHttpClient();
    }

    public Exchange receive() {
        return doReceive(-1);
    }

    public Exchange receive(long timeout) {
        return doReceive((int) timeout);
    }

    public Exchange receiveNoWait() {
        return doReceive(-1);
    }

    protected Exchange doReceive(int timeout) {
        Exchange exchange = endpoint.createExchange();
        HttpRequestBase method = createMethod();

        // set optional timeout in millis
        if (timeout > 0) {
            HttpConnectionParams.setSoTimeout(method.getParams(), timeout);
        }

        HttpEntity responeEntity = null;
        try {
            HttpResponse response = httpClient.execute(method);
            int responseCode = response.getStatusLine().getStatusCode();
            responeEntity = response.getEntity();
            // lets store the result in the output message.
            LoadingByteArrayOutputStream bos = new LoadingByteArrayOutputStream();
            InputStream is = responeEntity.getContent();

            try {
                IOHelper.copy(is, bos);
                bos.flush();
            } finally {
                ObjectHelper.close(is, "input stream", null);
            }
            Message message = exchange.getIn();
            message.setBody(bos.createInputStream());

            // lets set the headers
            Header[] headers = response.getAllHeaders();
            HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();
            for (Header header : headers) {
                String name = header.getName();
                String value = header.getValue();
                if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value, exchange)) {
                    message.setHeader(name, value);
                }
            }

            message.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
            return exchange;
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        } finally {
            if (responeEntity != null) {
                try {
                    responeEntity.consumeContent();
                } catch (IOException e) {
                    // nothing what we can do
                }
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected HttpRequestBase createMethod() {
        String uri = endpoint.getEndpointUri();
        return new HttpGet(uri);
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }
}