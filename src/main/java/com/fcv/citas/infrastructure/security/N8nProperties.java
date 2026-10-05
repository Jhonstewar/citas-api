package com.fcv.citas.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Publicacion de eventos hacia n8n (HU-035). Todo llega por variables de entorno.
 *
 * <p>Una URL vacia desactiva la publicacion de ese flujo. Una URL no vacia debe empezar por
 * {@code https://}; si no, el arranque falla. El secreto no se registra en log.</p>
 *
 * @param secret   {@code N8N_WEBHOOK_SECRET}, firma de los webhooks salientes.
 * @param wf001Url {@code N8N_WEBHOOK_WF001_URL}.
 * @param wf002Url {@code N8N_WEBHOOK_WF002_URL}.
 * @param wf003Url {@code N8N_WEBHOOK_WF003_URL}.
 */
@ConfigurationProperties(prefix = "app.n8n")
public record N8nProperties(String secret, String wf001Url, String wf002Url, String wf003Url) {

    private static final String HTTPS = "https://";

    public N8nProperties {
        secret = secret == null ? "" : secret;
        wf001Url = requireHttpsOrEmpty("N8N_WEBHOOK_WF001_URL", wf001Url);
        wf002Url = requireHttpsOrEmpty("N8N_WEBHOOK_WF002_URL", wf002Url);
        wf003Url = requireHttpsOrEmpty("N8N_WEBHOOK_WF003_URL", wf003Url);
    }

    private static String requireHttpsOrEmpty(String variable, String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (!url.startsWith(HTTPS)) {
            // No se incluye la URL: puede llevar un token en la ruta.
            throw new IllegalStateException(variable + " debe empezar por " + HTTPS + " o estar vacia");
        }
        return url;
    }

    public boolean wf001Enabled() {
        return !wf001Url.isEmpty();
    }

    public boolean wf002Enabled() {
        return !wf002Url.isEmpty();
    }

    public boolean wf003Enabled() {
        return !wf003Url.isEmpty();
    }

    @Override
    public String toString() {
        // Las URLs de webhook pueden contener un token en la ruta: solo se indica si estan activas.
        return "N8nProperties[secret=***, wf001=%s, wf002=%s, wf003=%s]"
                .formatted(wf001Enabled(), wf002Enabled(), wf003Enabled());
    }
}
