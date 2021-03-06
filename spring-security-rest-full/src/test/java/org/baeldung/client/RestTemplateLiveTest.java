package org.baeldung.client;

import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.baeldung.Consts.APPLICATION_PORT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.baeldung.persistence.model.Foo;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;

public class RestTemplateLiveTest {

    private RestTemplate restTemplate;
    private List<HttpMessageConverter<?>> messageConverters;
    private static final String fooResourceUrl = "http://localhost:" + APPLICATION_PORT + "/spring-security-rest-full/foos";

    @Before
    public void beforeTest() {
        restTemplate = new RestTemplate(getClientHttpRequestFactory());

        messageConverters = new ArrayList<>();
        final MappingJackson2HttpMessageConverter jsonMessageConverter = new MappingJackson2HttpMessageConverter();
        jsonMessageConverter.setObjectMapper(new ObjectMapper());
        messageConverters.add(jsonMessageConverter);

        ensureOneEntityExists();
    }

    @Test
    public void givenResourceUrl_whenSendGetForRequestEntity_thenStatusOk() throws IOException {
        final ResponseEntity<String> response = restTemplate.getForEntity(fooResourceUrl + "/1", String.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
    }

    @Test
    public void givenResourceUrl_whenSendGetForRestEntity_thenReceiveCorrectJson() throws IOException {
        final ResponseEntity<String> response = restTemplate.getForEntity(fooResourceUrl + "/1", String.class);

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(response.getBody());

        final JsonNode name = root.path("name");
        assertThat(name.asText(), is("bar"));

        final JsonNode owner = root.path("id");
        assertThat(owner.asText(), is("1"));
    }

    @Test
    public void givenResourceUrl_whenSendGetForObject_thenReturnsRepoObject() {
        restTemplate.setMessageConverters(messageConverters);
        final Foo foo = restTemplate.getForObject(fooResourceUrl + "/1", Foo.class);
        assertThat(foo.getName(), is("bar"));
        assertThat(foo.getId(), is(1L));
    }

    @Test
    public void givenFooService_whenPostForObject_thenCreatedObjectIsReturned() {
        final HttpEntity<Foo> request = new HttpEntity<>(new Foo("bar"));
        final Foo foo = restTemplate.postForObject(fooResourceUrl, request, Foo.class);
        assertThat(foo, notNullValue());
        assertThat(foo.getName(), is("bar"));
    }

    @Test
    public void givenFooService_whenPostFor2Objects_thenNewObjectIsCreatedEachTime() {
        final HttpEntity<Foo> request = new HttpEntity<>(new Foo("bar"));
        final Foo firstInstance = restTemplate.postForObject(fooResourceUrl, request, Foo.class);
        final Foo secondInstance = restTemplate.postForObject(fooResourceUrl, request, Foo.class);
        assertThat(firstInstance, notNullValue());
        assertThat(secondInstance, notNullValue());
        assertThat(firstInstance.getId(), not(secondInstance.getId()));
    }

    @Test
    public void givenFooService_whenCallHeadForHeaders_thenReceiveAllHeadersForThatResource() {
        final HttpHeaders httpHeaders = restTemplate.headForHeaders(fooResourceUrl);
        assertTrue(httpHeaders.getContentType().includes(MediaType.APPLICATION_JSON));
        assertTrue(httpHeaders.get("bar").contains("baz"));
    }

    @Test
    public void givenFooService_whenCallOptionsForAllow_thenReceiveValueOfAllowHeader() {
        final Set<HttpMethod> optionsForAllow = restTemplate.optionsForAllow(fooResourceUrl);
        final HttpMethod[] supportedMethods = { HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE };
        assertTrue(optionsForAllow.containsAll(Arrays.asList(supportedMethods)));
    }

    @Test
    public void givenFooService_whenPostResource_thenResourceIsCreated() {
        final RestTemplate template = new RestTemplate();

        final HttpHeaders headers = prepareBasicAuthHeaders();
        final HttpEntity<Foo> request = new HttpEntity<>(new Foo("bar"), headers);

        final ResponseEntity<Foo> response = template.exchange(fooResourceUrl, HttpMethod.POST, request, Foo.class);
        assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
        final Foo foo = response.getBody();
        assertThat(foo, notNullValue());
        assertThat(foo.getName(), is("bar"));
    }

    @Test
    public void givenFooService_whenPutExistingEntity_thenItIsUpdated() {
        final RestTemplate template = new RestTemplate();
        final HttpHeaders headers = prepareBasicAuthHeaders();
        final HttpEntity<Foo> request = new HttpEntity<>(new Foo("bar"), headers);

        // Create entity
        ResponseEntity<Foo> response = template.exchange(fooResourceUrl, HttpMethod.POST, request, Foo.class);
        assertThat(response.getStatusCode(), is(HttpStatus.CREATED));

        // Update entity
        final Foo updatedInstance = new Foo("newName");
        updatedInstance.setId(response.getBody().getId());
        final String resourceUrl = fooResourceUrl + '/' + response.getBody().getId();
        template.execute(resourceUrl, HttpMethod.PUT, requestCallback(updatedInstance), clientHttpResponse -> null);

        // Check that entity was updated
        response = template.exchange(resourceUrl, HttpMethod.GET, new HttpEntity<>(headers), Foo.class);
        final Foo foo = response.getBody();
        assertThat(foo.getName(), is(updatedInstance.getName()));
    }

    @Test
    public void givenFooService_whenCallDelete_thenEntityIsRemoved() {
        final Foo foo = new Foo("remove me");
        final ResponseEntity<Foo> response = restTemplate.postForEntity(fooResourceUrl, foo, Foo.class);
        assertThat(response.getStatusCode(), is(HttpStatus.CREATED));

        final String entityUrl = fooResourceUrl + "/" + response.getBody().getId();
        restTemplate.delete(entityUrl);
        try {
            restTemplate.getForEntity(entityUrl, Foo.class);
            fail();
        } catch (final HttpClientErrorException ex) {
            assertThat(ex.getStatusCode(), is(HttpStatus.NOT_FOUND));
        }
    }

    private void ensureOneEntityExists() {
        final Foo instance = new Foo("bar");
        instance.setId(1L);

        try {
            restTemplate.getForEntity(fooResourceUrl + "/1", Foo.class);
        } catch (final HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                restTemplate.postForEntity(fooResourceUrl, instance, Foo.class);
            }
        }

    }

    private ClientHttpRequestFactory getClientHttpRequestFactory() {
        final int timeout = 5;
        final RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout * 1000).setConnectionRequestTimeout(timeout * 1000).setSocketTimeout(timeout * 1000).build();

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("localhost", APPLICATION_PORT, AuthScope.ANY_REALM), new UsernamePasswordCredentials("user1", "user1Pass"));

        final CloseableHttpClient client = HttpClientBuilder.create().setDefaultRequestConfig(config).setDefaultCredentialsProvider(credentialsProvider).build();

        return new HttpComponentsClientHttpRequestFactory(client);
    }

    private HttpHeaders prepareBasicAuthHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        final String encodedLogPass = getBase64EncodedLogPass();
        headers.add(HttpHeaders.AUTHORIZATION, "Basic " + encodedLogPass);
        return headers;
    }

    private String getBase64EncodedLogPass() {
        final String logPass = "user1:user1Pass";
        final byte[] authHeaderBytes = encodeBase64(logPass.getBytes(Charsets.US_ASCII));
        return new String(authHeaderBytes, Charsets.US_ASCII);
    }

    private RequestCallback requestCallback(final Foo updatedInstance) {
        return clientHttpRequest -> {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(clientHttpRequest.getBody(), updatedInstance);
            clientHttpRequest.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            clientHttpRequest.getHeaders().add(HttpHeaders.AUTHORIZATION, "Basic " + getBase64EncodedLogPass());
        };
    }
}
