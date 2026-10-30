package com.schoolerp.notification.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Meta's WhatsApp Cloud API: {@code POST /{version}/{phone-number-id}/messages}
 * with a template message. Numbers are Indian mobiles, sent as {@code 91XXXXXXXXXX}.
 */
class CloudApiWhatsAppSender implements WhatsAppSender {

	/** Template language; the templates are created in English ("en"). */
	private static final String LANGUAGE = "en";

	private final RestClient client;

	private final String phoneNumberId;

	CloudApiWhatsAppSender(String baseUrl, String apiVersion, String phoneNumberId, String accessToken) {
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		requests.setReadTimeout(Duration.ofSeconds(15));
		this.client = RestClient.builder()
			.baseUrl(baseUrl + "/" + apiVersion)
			.requestFactory(requests)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.build();
		this.phoneNumberId = phoneNumberId;
	}

	@Override
	public boolean isConnected() {
		return true;
	}

	@Override
	public SendResult send(String phone, String template, List<String> params) {
		Map<String, Object> body = Map.of("messaging_product", "whatsapp", "to", "91" + phone, "type", "template",
				"template", Map.of("name", template, "language", Map.of("code", LANGUAGE), "components",
						List.of(Map.of("type", "body", "parameters",
								params.stream().map(p -> Map.of("type", "text", "text", p)).toList()))));
		try {
			Map<?, ?> response = client.post()
				.uri("/{id}/messages", phoneNumberId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(Map.class);
			return SendResult.ok(messageId(response));
		}
		catch (RestClientResponseException ex) {
			return SendResult.failed(errorText(ex));
		}
		catch (RestClientException ex) {
			return SendResult.failed("Could not reach WhatsApp. Try again in a minute.");
		}
	}

	/** {"messages":[{"id":"wamid..."}]} */
	private static String messageId(Map<?, ?> response) {
		if (response != null && response.get("messages") instanceof List<?> list && !list.isEmpty()
				&& list.getFirst() instanceof Map<?, ?> first && first.get("id") != null) {
			return String.valueOf(first.get("id"));
		}
		return null;
	}

	/** Meta's {"error":{"message": ...}}, or the HTTP status. */
	private static String errorText(RestClientResponseException ex) {
		try {
			Map<?, ?> body = ex.getResponseBodyAs(Map.class);
			if (body != null && body.get("error") instanceof Map<?, ?> error && error.get("message") != null) {
				return "WhatsApp: " + error.get("message");
			}
		}
		catch (RuntimeException ignored) {
			// not JSON; fall back to the status
		}
		return "WhatsApp answered " + ex.getStatusCode().value();
	}

}
