package qa.cinepolis.runner;

import java.util.Map;

/**
 * Fuente de verdad de qué Apple Developer Team(s) tienen una cuenta Apple ID
 * autenticada ahora mismo — es decir, si Xcode podría compilar y firmar hoy
 * con ese Team, o respondería "No Account for Team X".
 *
 * AppleDeveloperTeamManager (y cualquier otra clase del Runner) solo conoce
 * esta interfaz: nunca sabe dónde vive este registro ni cómo está codificado.
 * Eso permite cambiar la fuente, o su formato interno, sin tocar la política
 * de selección de Team. Ver XcodeAccountProvider para la única implementación
 * y el detalle de qué archivo/clave consulta.
 */
interface AppleDeveloperAccountProvider {

    /**
     * @param available         false si la fuente no pudo consultarse — el llamador
     *                          decide cómo degradar (ver unavailableReason); nunca
     *                          debe interpretarse como "ningún Team autenticado".
     * @param teamNamesById     Team ID → nombre legible, solo de Teams con cuenta
     *                          autenticada ahora mismo. Vacío si available=false.
     * @param unavailableReason motivo legible cuando available=false, o null.
     */
    record DiscoveryResult(boolean available, Map<String, String> teamNamesById, String unavailableReason) {

        static DiscoveryResult available(Map<String, String> teamNamesById) {
            return new DiscoveryResult(true, teamNamesById, null);
        }

        static DiscoveryResult unavailable(String reason) {
            return new DiscoveryResult(false, Map.of(), reason);
        }

        boolean isTeamAuthenticated(String teamId) {
            return available && teamNamesById.containsKey(teamId);
        }
    }

    /** Nunca lanza excepciones — cualquier fallo se traduce en DiscoveryResult.unavailable(). */
    DiscoveryResult discoverAuthenticatedTeams();
}
