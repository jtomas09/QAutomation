"""
Addon de mitmproxy para Network Monitoring (RunnerAgent).

Responsabilidad ÚNICA de este script: serializar cada flow HTTP(S) completado y
cada resultado de handshake TLS a un archivo NDJSON (una línea JSON por evento),
para que el proceso Java (NetworkMonitoringManager / NetworkMonitoringExtension)
lo lea después. NO hace clasificación de errores ni sanitización de datos
sensibles — eso vive del lado Java (NetworkEventClassifier/NetworkEventSanitizer),
para tener una única fuente de esa lógica, testeable con JUnit.

Cuerpos acotados a RAW_BODY_CAP_BYTES aquí (protección de memoria/disco básica) —
el recorte al límite REAL configurado por el usuario (maxResponseBodySize) y la
redacción de secretos ocurren del lado Java antes de escribir cualquier evidencia
persistente por-test.

Uso: mitmdump -s capture_addon.py --set events_file=<ruta> --set confdir=<ruta>
"""
import json
import time

from mitmproxy import http, tls, ctx

RAW_BODY_CAP_BYTES = 2 * 1024 * 1024  # 2 MB — tope de captura cruda, no el límite final de evidencia


class CaptureAddon:
    def __init__(self):
        self.events_file = None

    def load(self, loader):
        loader.add_option(
            name="events_file",
            typespec=str,
            default="",
            help="Ruta del archivo NDJSON donde se escribe cada evento capturado.",
        )

    def running(self):
        path = ctx.options.events_file
        if path:
            self.events_file = open(path, "a", buffering=1, encoding="utf-8")
            ctx.log.info(f"[NETWORK] capture_addon: escribiendo eventos en {path}")

    def done(self):
        if self.events_file:
            self.events_file.close()

    def _write(self, event: dict):
        if not self.events_file:
            return
        try:
            self.events_file.write(json.dumps(event, ensure_ascii=False) + "\n")
        except Exception as e:
            ctx.log.warn(f"[NETWORK] No se pudo escribir evento: {e}")

    def _client_ip(self, flow) -> str:
        try:
            return flow.client_conn.peername[0]
        except Exception:
            return "unknown"

    def _headers_dict(self, headers) -> dict:
        return {k: v for k, v in headers.items(multi=False)}

    def _body_text(self, content: bytes | None) -> tuple[str, bool]:
        if not content:
            return "", False
        truncated = len(content) > RAW_BODY_CAP_BYTES
        raw = content[:RAW_BODY_CAP_BYTES]
        try:
            return raw.decode("utf-8", errors="replace"), truncated
        except Exception:
            return "[BINARY]", truncated

    # ── Flows HTTP(S) completos ────────────────────────────────────────────────

    def response(self, flow: http.HTTPFlow) -> None:
        try:
            req = flow.request
            res = flow.response
            duration_ms = None
            if flow.response and flow.response.timestamp_end and flow.request.timestamp_start:
                duration_ms = int((flow.response.timestamp_end - flow.request.timestamp_start) * 1000)

            req_body, req_truncated = (("", False) if req is None
                                        else self._body_text(req.raw_content))
            res_body, res_truncated = (("", False) if res is None
                                        else self._body_text(res.raw_content))

            event = {
                "type": "http_flow",
                "clientIp": self._client_ip(flow),
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(req.timestamp_start)) + "Z",
                "epochMillis": int(req.timestamp_start * 1000),
                "method": req.method,
                "url": req.pretty_url,
                "host": req.pretty_host,
                "path": req.path.split("?")[0] if req.path else "",
                "query": dict(req.query) if req.query else {},
                "requestHeaders": self._headers_dict(req.headers),
                "requestBody": req_body,
                "requestBodyTruncated": req_truncated,
                "requestContentType": req.headers.get("content-type", ""),
                "statusCode": res.status_code if res else None,
                "responseHeaders": self._headers_dict(res.headers) if res else {},
                "responseBody": res_body,
                "responseBodyTruncated": res_truncated,
                "responseContentType": res.headers.get("content-type", "") if res else "",
                "durationMs": duration_ms,
                "networkErrorType": None,
            }
            self._write(event)
        except Exception as e:
            ctx.log.warn(f"[NETWORK] Error serializando flow: {e}")

    def error(self, flow: http.HTTPFlow) -> None:
        # Flows que nunca llegaron a tener response: timeout, conexión rechazada, DNS, etc.
        try:
            req = flow.request
            msg = str(flow.error) if flow.error else "unknown error"
            error_type = "CONNECTION_ERROR"
            low = msg.lower()
            if "timed out" in low or "timeout" in low:
                error_type = "TIMEOUT"
            elif "name or service not known" in low or "nodename nor servname" in low or "dns" in low:
                error_type = "DNS_ERROR"
            elif "ssl" in low or "certificate" in low or "tls" in low:
                error_type = "SSL_ERROR"
            elif "reset" in low:
                error_type = "CONNECTION_RESET"

            event = {
                "type": "http_flow",
                "clientIp": self._client_ip(flow),
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(req.timestamp_start)) + "Z",
                "epochMillis": int(req.timestamp_start * 1000) if req.timestamp_start else int(time.time() * 1000),
                "method": req.method if req else "",
                "url": req.pretty_url if req else "",
                "host": req.pretty_host if req else "",
                "path": (req.path.split("?")[0] if req and req.path else ""),
                "query": dict(req.query) if req and req.query else {},
                "requestHeaders": self._headers_dict(req.headers) if req else {},
                "requestBody": "",
                "requestBodyTruncated": False,
                "requestContentType": req.headers.get("content-type", "") if req else "",
                "statusCode": None,
                "responseHeaders": {},
                "responseBody": "",
                "responseBodyTruncated": False,
                "responseContentType": "",
                "durationMs": None,
                "networkErrorType": error_type,
                "networkErrorMessage": msg,
            }
            self._write(event)
        except Exception as e:
            ctx.log.warn(f"[NETWORK] Error serializando error de flow: {e}")

    # ── Handshake TLS — única señal usada para detectar si el dispositivo ya
    # confía en la CA (nunca se inspecciona el almacén de certificados del
    # dispositivo directamente — no es accesible sin root/jailbreak). ──────────

    def tls_established_client(self, data: tls.TlsData) -> None:
        self._write({
            "type": "tls_handshake",
            "clientIp": data.context.client.peername[0] if data.context.client.peername else "unknown",
            "result": "SUCCESS",
            "epochMillis": int(time.time() * 1000),
        })

    def tls_failed_client(self, data: tls.TlsData) -> None:
        self._write({
            "type": "tls_handshake",
            "clientIp": data.context.client.peername[0] if data.context.client.peername else "unknown",
            "result": "FAILED",
            "reason": str(data.conn.error) if getattr(data.conn, "error", None) else "unknown",
            "epochMillis": int(time.time() * 1000),
        })


addons = [CaptureAddon()]
