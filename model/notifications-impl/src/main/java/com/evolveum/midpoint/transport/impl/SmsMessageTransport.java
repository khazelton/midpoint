/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.transport.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import com.evolveum.midpoint.model.common.expression.ModelExpressionThreadLocalHolder;
import com.evolveum.midpoint.notifications.api.events.Event;
import com.evolveum.midpoint.notifications.api.transports.Message;
import com.evolveum.midpoint.notifications.api.transports.Transport;
import com.evolveum.midpoint.notifications.api.transports.TransportSupport;
import com.evolveum.midpoint.notifications.impl.util.HttpUtil;
import com.evolveum.midpoint.prism.MutablePrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.repo.common.expression.Expression;
import com.evolveum.midpoint.repo.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

/**
 * Message transport sending SMS messages.
 */
public class SmsMessageTransport implements Transport<SmsTransportConfigurationType> {

    private static final Trace LOGGER = TraceManager.getTrace(SmsMessageTransport.class);

    private static final String DOT_CLASS = SmsMessageTransport.class.getName() + ".";

    private String name;
    private SmsTransportConfigurationType configuration;
    private TransportSupport transportSupport;

    @Override
    public void configure(
            @NotNull SmsTransportConfigurationType configuration,
            @NotNull TransportSupport transportSupport) {
        this.configuration = Objects.requireNonNull(configuration);
        name = Objects.requireNonNull(configuration.getName());
        this.transportSupport = transportSupport;
    }

    @Override
    public void send(Message message, String transportName, Event event, Task task, OperationResult parentResult) {
        OperationResult result = parentResult.createSubresult(DOT_CLASS + "send");
        result.addArbitraryObjectCollectionAsParam("message recipient(s)", message.getTo());
        result.addParam("message subject", message.getSubject());

        String logToFile = configuration.getLogToFile();
        if (logToFile != null) {
            TransportUtil.logToFile(logToFile, TransportUtil.formatToFileNew(message, transportName), LOGGER);
        }
        String file = configuration.getRedirectToFile();
        int optionsForFilteringRecipient = TransportUtil.optionsForFilteringRecipient(configuration);

        List<String> allowedRecipientTo = new ArrayList<>();
        List<String> forbiddenRecipientTo = new ArrayList<>();

        if (optionsForFilteringRecipient != 0) {
            TransportUtil.validateRecipient(
                    allowedRecipientTo, forbiddenRecipientTo, message.getTo(), configuration, task, result,
                    transportSupport.expressionFactory(), MiscSchemaUtil.getExpressionProfile(), LOGGER);

            if (file != null) {
                if (!forbiddenRecipientTo.isEmpty()) {
                    message.setTo(forbiddenRecipientTo);
                    writeToFile(message, file, null, emptyList(), null, result);
                }
                message.setTo(allowedRecipientTo);
            }

        } else if (file != null) {
            writeToFile(message, file, null, emptyList(), null, result);
            return;
        }

        if (configuration.getGateway().isEmpty()) {
            String msg = "SMS gateway(s) are not defined, notification to " + message.getTo() + " will not be sent.";
            LOGGER.warn(msg);
            result.recordWarning(msg);
            return;
        }

        String from;
        if (message.getFrom() != null) {
            from = message.getFrom();
        } else if (configuration.getDefaultFrom() != null) {
            from = configuration.getDefaultFrom();
        } else {
            from = "";
        }

        if (message.getTo().isEmpty()) {
            if (optionsForFilteringRecipient != 0) {
                String msg = "After recipient validation there is no recipient to send the notification to.";
                LOGGER.debug(msg);
                result.recordSuccess();
            } else {
                String msg = "There is no recipient to send the notification to.";
                LOGGER.warn(msg);
                result.recordWarning(msg);
            }
            return;
        }

        List<String> to = message.getTo();
        assert to.size() > 0;

        for (SmsGatewayConfigurationType smsGatewayConfigurationType : configuration.getGateway()) {
            OperationResult resultForGateway = result.createSubresult(DOT_CLASS + "send.forGateway");
            resultForGateway.addContext("gateway name", smsGatewayConfigurationType.getName());
            try {
                VariablesMap variables = getDefaultVariables(from, to, message);
                HttpMethodType method = defaultIfNull(smsGatewayConfigurationType.getMethod(), HttpMethodType.GET);
                ExpressionType urlExpression = defaultIfNull(smsGatewayConfigurationType.getUrlExpression(), null);
                String url = evaluateExpressionChecked(urlExpression, variables, "sms gateway request url", task, result);
                String proxyHost = smsGatewayConfigurationType.getProxyHost();
                String proxyPort = smsGatewayConfigurationType.getProxyPort();
                LOGGER.debug("Sending SMS to URL {} via proxy host {} and port {} (method {})", url, proxyHost, proxyPort, method);
                if (url == null) {
                    throw new IllegalArgumentException("No URL specified");
                }
                List<String> headersList = evaluateExpressionsChecked(smsGatewayConfigurationType.getHeadersExpression(), variables,
                        "sms gateway request headers", task, result);
                LOGGER.debug("Using request headers:\n{}", headersList);

                String encoding = defaultIfNull(smsGatewayConfigurationType.getBodyEncoding(), StandardCharsets.ISO_8859_1.name());
                String body = evaluateExpressionChecked(smsGatewayConfigurationType.getBodyExpression(), variables,
                        "sms gateway request body", task, result);
                LOGGER.debug("Using request body text (encoding: {}):\n{}", encoding, body);

                if (smsGatewayConfigurationType.getLogToFile() != null) {
                    TransportUtil.logToFile(smsGatewayConfigurationType.getLogToFile(), formatToFile(message, url, headersList, body), LOGGER);
                }
                if (smsGatewayConfigurationType.getRedirectToFile() != null) {
                    writeToFile(message, smsGatewayConfigurationType.getRedirectToFile(), url, headersList, body, resultForGateway);
                    result.computeStatus();
                    return;
                } else {
                    HttpClientBuilder builder = HttpClientBuilder.create();
                    String username = smsGatewayConfigurationType.getUsername();
                    ProtectedStringType password = smsGatewayConfigurationType.getPassword();
                    CredentialsProvider provider = new BasicCredentialsProvider();
                    if (username != null) {
                        String plainPassword = password != null ? transportSupport.protector().decryptString(password) : null;
                        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, plainPassword);
                        provider.setCredentials(AuthScope.ANY, credentials);
                        builder = builder.setDefaultCredentialsProvider(provider);
                    }
                    String proxyUsername = smsGatewayConfigurationType.getProxyUsername();
                    ProtectedStringType proxyPassword = smsGatewayConfigurationType.getProxyPassword();
                    if (StringUtils.isNotBlank(proxyHost)) {
                        HttpHost proxy;
                        if (StringUtils.isNotBlank(proxyPort) && isInteger(proxyPort)) {
                            int port = Integer.parseInt(proxyPort);
                            proxy = new HttpHost(proxyHost, port);
                        } else {
                            proxy = new HttpHost(proxyHost);
                        }
                        if (StringUtils.isNotBlank(proxyUsername)) {
                            String plainProxyPassword = proxyPassword != null ? transportSupport.protector().decryptString(proxyPassword) : null;
                            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(proxyUsername, plainProxyPassword);
                            provider.setCredentials(new AuthScope(proxy), credentials);
                        }
                        builder = builder.setDefaultCredentialsProvider(provider);
                        builder = builder.setProxy(proxy);
                    }

                    HttpClient client = builder.build();
                    HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(client);
                    ClientHttpRequest request = requestFactory.createRequest(new URI(url), HttpUtil.toHttpMethod(method));
                    setHeaders(request, headersList);
                    if (body != null) {
                        request.getBody().write(body.getBytes(encoding));
                    }
                    ClientHttpResponse response = request.execute();
                    LOGGER.debug("Result: " + response.getStatusCode() + "/" + response.getStatusText());
                    if (response.getStatusCode().series() != HttpStatus.Series.SUCCESSFUL) {
                        throw new SystemException("SMS gateway communication failed: " + response.getStatusCode() + ": " + response.getStatusText());
                    }
                    LOGGER.debug("Message sent successfully to {} via gateway {}.", message.getTo(), smsGatewayConfigurationType.getName());
                    resultForGateway.recordSuccess();
                    result.recordSuccess();
                    return;
                }
            } catch (Throwable t) {
                String msg = "Couldn't send SMS to " + message.getTo() + " via " + smsGatewayConfigurationType.getName() + ", trying another gateway, if there is any";
                LoggingUtils.logException(LOGGER, msg, t);
                resultForGateway.recordFatalError(msg, t);
            }
        }
        LOGGER.warn("No more SMS gateways to try, notification to " + message.getTo() + " will not be sent.");
        result.recordWarning("Notification to " + message.getTo() + " could not be sent.");
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
        return true;
    }

    private void setHeaders(ClientHttpRequest request, List<String> headersList) {
        for (String headerAsString : headersList) {
            if (StringUtils.isEmpty(headerAsString)) {
                continue;
            }
            int i = headerAsString.indexOf(':');
            if (i < 0) {
                throw new IllegalArgumentException("Illegal header specification (expected was 'name: value' pair): " + headerAsString);
            }
            String headerName = headerAsString.substring(0, i);
            int headerValueIndex;
            if (i + 1 == headerAsString.length() || headerAsString.charAt(i + 1) != ' ') {
                // let's be nice and treat well the wrong case (there's no space after ':')
                headerValueIndex = i + 1;
            } else {
                // correct case: ':' followed by space
                headerValueIndex = i + 2;
            }
            String headerValue = headerAsString.substring(headerValueIndex);
            request.getHeaders().add(headerName, headerValue);
        }
    }

    private void writeToFile(Message message, String file, String url, List<String> headers, String body, OperationResult result) {
        try {
            TransportUtil.appendToFile(file, formatToFile(message, url, headers, body));
            result.recordSuccess();
        } catch (IOException e) {
            LoggingUtils.logException(LOGGER, "Couldn't write to SMS redirect file {}", e, file);
            result.recordPartialError("Couldn't write to SMS redirect file " + file, e);
        }
    }

    private String formatToFile(Message mailMessage, String url, List<String> headers, String body) {
        return "================ " + new Date() + " ======= " + (url != null ? url : "")
                + "\nHeaders:\n" + headers
                + "\n\nBody:\n" + body
                + "\n\nFor message:\n" + mailMessage.toString() + "\n\n";
    }

    private String evaluateExpressionChecked(ExpressionType expressionType, VariablesMap VariablesMap,
            String shortDesc, Task task, OperationResult result) {
        try {
            return evaluateExpression(expressionType, VariablesMap, false, shortDesc, task, result).get(0);
        } catch (ObjectNotFoundException | SchemaException | ExpressionEvaluationException | CommunicationException | ConfigurationException | SecurityViolationException e) {
            LoggingUtils.logException(LOGGER, "Couldn't evaluate {} {}", e, shortDesc, expressionType);
            result.recordFatalError("Couldn't evaluate " + shortDesc, e);
            throw new SystemException(e);
        }
    }

    @NotNull
    private List<String> evaluateExpressionsChecked(ExpressionType expressionType, VariablesMap VariablesMap,
            @SuppressWarnings("SameParameterValue") String shortDesc, Task task, OperationResult result) {
        try {
            return evaluateExpression(expressionType, VariablesMap, true, shortDesc, task, result);
        } catch (ObjectNotFoundException | SchemaException | ExpressionEvaluationException | CommunicationException | ConfigurationException | SecurityViolationException e) {
            LoggingUtils.logException(LOGGER, "Couldn't evaluate {} {}", e, shortDesc, expressionType);
            result.recordFatalError("Couldn't evaluate " + shortDesc, e);
            throw new SystemException(e);
        }
    }

    // A little hack: for single-value cases we always return single-item list (even if the returned value is null)
    @NotNull
    private List<String> evaluateExpression(ExpressionType expressionType, VariablesMap VariablesMap,
            boolean multipleValues, String shortDesc, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException,
            ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
        if (expressionType == null) {
            return multipleValues ? emptyList() : singletonList(null);
        }
        QName resultName = new QName(SchemaConstants.NS_C, "result");
        MutablePrismPropertyDefinition<String> resultDef =
                transportSupport.prismContext().definitionFactory().createPropertyDefinition(resultName, DOMUtil.XSD_STRING);
        if (multipleValues) {
            resultDef.setMaxOccurs(-1);
        }

        Expression<PrismPropertyValue<String>, PrismPropertyDefinition<String>> expression =
                transportSupport.expressionFactory().makeExpression(
                        expressionType, resultDef, MiscSchemaUtil.getExpressionProfile(), shortDesc, task, result);
        ExpressionEvaluationContext params = new ExpressionEvaluationContext(null, VariablesMap, shortDesc, task);
        PrismValueDeltaSetTriple<PrismPropertyValue<String>> exprResult = ModelExpressionThreadLocalHolder
                .evaluateExpressionInContext(expression, params, task, result);

        if (!multipleValues) {
            if (exprResult.getZeroSet().size() > 1) {
                throw new SystemException("Invalid number of return values (" + exprResult.getZeroSet().size() + "), expected at most 1.");
            } else if (exprResult.getZeroSet().isEmpty()) {
                return singletonList(null);
            } else {
                // single-valued response is treated below
            }
        }
        return exprResult.getZeroSet().stream().map(ppv -> ppv.getValue()).collect(Collectors.toList());
    }

    protected VariablesMap getDefaultVariables(String from, List<String> to, Message message)
            throws UnsupportedEncodingException {
        VariablesMap variables = new VariablesMap();
        variables.put(ExpressionConstants.VAR_FROM, from, String.class);
        variables.put(ExpressionConstants.VAR_ENCODED_FROM,
                URLEncoder.encode(from, StandardCharsets.US_ASCII), String.class);
        variables.put(ExpressionConstants.VAR_TO, to.get(0), String.class);
        variables.put(ExpressionConstants.VAR_TO_LIST, to, List.class);
        List<String> encodedTo = new ArrayList<>();
        for (String s : to) {
            encodedTo.add(URLEncoder.encode(s, StandardCharsets.US_ASCII));
        }
        variables.put(ExpressionConstants.VAR_ENCODED_TO, encodedTo.get(0), String.class);
        variables.put(ExpressionConstants.VAR_ENCODED_TO_LIST, encodedTo, List.class);
        variables.put(ExpressionConstants.VAR_MESSAGE_TEXT, message.getBody(), String.class);
        variables.put(ExpressionConstants.VAR_ENCODED_MESSAGE_TEXT,
                URLEncoder.encode(message.getBody(), StandardCharsets.US_ASCII), String.class);
        variables.put(ExpressionConstants.VAR_MESSAGE, message, Message.class);
        return variables;
    }

    @Override
    public String getDefaultRecipientAddress(FocusType recipient) {
        return recipient.getTelephoneNumber();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SmsTransportConfigurationType getConfiguration() {
        return configuration;
    }
}
