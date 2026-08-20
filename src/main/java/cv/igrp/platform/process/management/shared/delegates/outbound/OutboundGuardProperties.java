package cv.igrp.platform.process.management.shared.delegates.outbound;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * SSRF policy for outbound calls whose destination comes from process variables or BPMN expressions
 * (webhook and external-assignment delegates).
 *
 * <p>Private, loopback, link-local, CGNAT and unique-local destinations are always blocked unless the
 * host is explicitly allowlisted — an allowlisted host is trusted as configured and skips resolution.
 *
 * @param requireHttps        require https on outbound URLs (development profile turns this off)
 * @param allowedHosts        exact hosts or {@code *.suffix} wildcards; empty = any public host;
 *                            non-empty = only these hosts
 * @param allowedExtraHeaders custom header names (from process variables) allowed on top of the safe
 *                            set; credential and proxy headers are dropped unless listed here
 * @param allowedEnvVars      environment variable names a process definition may reference via
 *                            {@code $[VAR]}; exact names or {@code PREFIX*} wildcards. Governs ALL
 *                            delegate env-var resolution, not just outbound calls.
 * @param maxResponseBytes    upper bound for webhook response bodies
 */
@ConfigurationProperties(prefix = "igrp.delegate.outbound")
public record OutboundGuardProperties(
    @DefaultValue("true") boolean requireHttps,
    List<String> allowedHosts,
    List<String> allowedExtraHeaders,
    @DefaultValue("IGRP_WEBHOOK_*") List<String> allowedEnvVars,
    @DefaultValue("1048576") long maxResponseBytes
) {
}
