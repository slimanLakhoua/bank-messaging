package com.bank.messaging.documentation;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MVC auto-configuration and bundled resources, without a database or broker. */
@SpringBootTest(classes = SwaggerDocumentationTest.DocumentationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "management.endpoint.health.group.readiness.include=readinessState")
class SwaggerDocumentationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
            "com.ibm.mq.spring.boot.MQAutoConfiguration"
    })
    static class DocumentationApplication {
    }

    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void prepareMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void swaggerEntryRedirectsToTheBundledUi() throws Exception {
        var response = mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andReturn().getResponse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/swagger-ui/index.html");
        var html = mvc.perform(get(response.getRedirectedUrl())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("swagger-ui-bundle.js", "swagger-ui.css");
    }

    @Test
    void swaggerResourcesAndInitializerAreServedLocally() throws Exception {
        for (String resource : new String[]{"swagger-ui-bundle.js", "swagger-ui-standalone-preset.js", "swagger-ui.css"}) {
            var body = mvc.perform(get("/swagger-ui/" + resource)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(body).isNotEmpty();
        }
        var initializer = mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(initializer).contains("/v3/api-docs/swagger-config").doesNotContain("https://petstore.swagger.io");
    }

    @Test
    void swaggerUsesTheExistingContractWithoutAnExternalValidator() throws Exception {
        var body = mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var config = new JSONObject(body);
        assertThat(config.getString("url")).isEqualTo("/openapi.json");
        assertThat(config.getString("validatorUrl")).isEqualTo("none");
        // The maintained contract is authoritative; do not expose a second inferred specification.
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void contractTargetsTheCurrentOriginAndPreservesBothConsultationOperations() throws Exception {
        var body = mvc.perform(get("/openapi.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var contract = new JSONObject(body);
        assertThat(contract.getString("openapi")).isEqualTo("3.1.0");
        assertThat(contract.getJSONArray("servers").getJSONObject(0).getString("url")).isEqualTo("/");
        assertThat(contract.getJSONObject("paths").keySet())
                .containsExactlyInAnyOrder("/api/messages", "/api/messages/{id}");
        assertThat(contract.getJSONObject("paths").getJSONObject("/api/messages").getJSONObject("get")
                .getJSONArray("parameters").length()).isEqualTo(7);
        assertThat(contract.getJSONObject("components").getJSONObject("schemas")
                .getJSONObject("MessageSummary").getJSONObject("properties").has("payload")).isFalse();
    }
}
